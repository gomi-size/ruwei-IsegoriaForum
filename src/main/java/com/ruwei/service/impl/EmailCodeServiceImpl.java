package com.ruwei.service.impl;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.config.MailProperties;
import com.ruwei.domain.Enum.EmailScene;
import com.ruwei.domain.constant.EmailCodeKeys;
import com.ruwei.exception.BusinessException;
import com.ruwei.manager.MailSender;
import com.ruwei.service.EmailCodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Date;

/**
 * 邮箱验证码服务实现。
 *
 * <p>验证码生命周期完全由 Redis 承担：{@code SETEX} 管过期、{@code DEL} 管一次性消费，
 * 因此不需要验证码表、不需要定时清理任务、不需要额外索引。</p>
 *
 * @author Administrator
 */
@Slf4j
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    /**
     * 验证码与错误计数的存活时间（秒）。
     * <p>与发码侧写入时的 TTL 保持一致，错误计数沿用同一时长即可。</p>
     */
    private static final long CODE_TTL_SECONDS = 300L;

    /**
     * 密码学安全的随机数发生器。
     * <p>验证码是安全凭证，禁止使用 {@code ThreadLocalRandom}
     * （Hutool 的 {@code RandomUtil.randomNumbers()} 底层即为后者），其序列可被预测。</p>
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 验证码位数：6 位，取值 000000 ~ 999999。
     */
    private static final int CODE_BOUND = 1_000_000;

    /**
     * 校验错误次数上限：达到即作废验证码，强制用户重新获取。
     * <p>6 位数字共 100 万种组合，5 次尝试的命中概率约百万分之五，足以抵御暴力猜测。</p>
     */
    private static final long FAIL_LIMIT = 5L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private MailSender mailSender;

    @Resource
    private MailProperties mailProperties;

    /**
     * 发送验证码
     * @param email    目标邮箱
     * @param scene    使用场景（决定 key 命名空间与邮件文案）
     * @param clientIp 客户端 IP，用于 IP 维度日限流；可为 null（则跳过该维度）
     */
    @Override
    public void sendCode(String email, EmailScene scene, String clientIp) {
        ThrowUtils.throwIf(StrUtil.isBlank(email) || scene == null,
                ErrorCode.PARAMS_ERROR, "邮箱与验证码场景均不能为空");

        ThrowUtils.throwIf(!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");

        //1) 日上限预检：只读不写，不消耗配额。
        ThrowUtils.throwIf(readCounter(EmailCodeKeys.dailyEmailKey(email))>=mailProperties.getDailyLimitPerEmail(),ErrorCode.RATE_LIMIT_ERROR,"改邮箱今日验证码获取次数已达上线");
        if (StrUtil.isNotBlank(clientIp)) {
            ThrowUtils.throwIf(readCounter(EmailCodeKeys.dailyIpKey(clientIp))
                            >= mailProperties.getDailyLimitPerIp(),
                    ErrorCode.RATE_LIMIT_ERROR, "当前网络今日验证码获取次数已达上限，请明日再试");
        }
        // 2) 冷却：SETNX 原子抢占。并发下只有一个请求能拿到，天然防止重复投递。
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(EmailCodeKeys.cooldownKey(email), "1", Duration.ofSeconds(mailProperties.getCooldownSeconds()));
        ThrowUtils.throwIf(!Boolean.TRUE.equals(acquired),
                ErrorCode.RATE_LIMIT_ERROR,
                "请求过于频繁，请 " + mailProperties.getCooldownSeconds() + " 秒后再试");
        // 3) 抢占成功后才真正消耗配额
        incrementDailyCounter(EmailCodeKeys.dailyEmailKey(email));

        // 4) 生成并写入 Redis。直接 SET 覆盖旧码：重发即让旧码立即失效。
        String code = generateCode();
        stringRedisTemplate.opsForValue().set(
                EmailCodeKeys.codeKey(scene, email), code,
                Duration.ofSeconds(mailProperties.getCodeTtlSeconds()));
        // 5) 异步投递。MailSender 实现内部 try-catch，失败只记日志不回传，
        //    因此「发信失败」时本接口仍返回成功 —— 用户可自行重试，无需感知。
        mailSender.sendVerifyCode(email, code, scene);
    }




    /**
     * 校验并消费验证码（一次性）。
     *
     * @param email 目标邮箱
     * @param scene 使用场景
     * @param code  用户提交的验证码明文
     */
    @Override
    public void consumeCode(String email, EmailScene scene, String code) {
        ThrowUtils.throwIf(StrUtil.isBlank(email) || scene == null || StrUtil.isBlank(code),
                ErrorCode.PARAMS_ERROR, "邮箱、验证码场景与验证码均不能为空");

        String codeKey = EmailCodeKeys.codeKey(scene, email);
        String failKey = EmailCodeKeys.failKey(scene, email);

        // 一态：无 key —— 验证码未申请、已过期，或已被并发请求消费
        String cached = stringRedisTemplate.opsForValue().get(codeKey);
        ThrowUtils.throwIf(StrUtil.isBlank(cached), ErrorCode.EMAIL_CODE_ERROR, "验证码已过期，请重新获取");

        // 二态：不匹配 —— 累加错误计数，达到上限则作废验证码
        // 用 MessageDigest.isEqual 做常量时间比较，避免 String.equals 提前返回带来的时序侧信道
        boolean matched = MessageDigest.isEqual(
                cached.trim().getBytes(StandardCharsets.UTF_8),
                code.trim().getBytes(StandardCharsets.UTF_8));
        if (!matched) {
            recordFail(failKey, codeKey);
            throw new BusinessException(ErrorCode.EMAIL_CODE_ERROR, "验证码错误");
        }

        // 三态：匹配 —— 原子消费。DEL 在 Redis 单线程内原子执行，
        // 返回值 true 的请求才是唯一抢到消费权的那个；false 说明已被并发请求取走。
        Boolean consumed = stringRedisTemplate.delete(codeKey);
        ThrowUtils.throwIf(!consumed,
                ErrorCode.EMAIL_CODE_ERROR, "验证码已过期，请重新获取");

        // 消费成功，清掉错误计数，避免影响下一次验证码
        stringRedisTemplate.delete(failKey);
    }




    /**
     * 记录一次校验失败。
     *
     * <p><b>TTL 陷阱</b>：{@code INCR} 对已存在的 key 不会刷新 TTL，
     * 因此只在首次创建（返回 1）时设置过期时间；否则每次输错都会续期，
     * 导致错误计数永不失效、用户被永久锁死。</p>
     *
     * <p>计数本身失败不影响主流程：验证码仍在 Redis 中，用户仍可继续尝试。</p>
     *
     * @param failKey 错误计数 key
     * @param codeKey 验证码 key（达到上限时作废）
     */
    private void recordFail(String failKey, String codeKey) {
        try {
            Long failCount = stringRedisTemplate.opsForValue().increment(failKey);
            if (failCount != null && failCount == 1L) {
                stringRedisTemplate.expire(failKey, Duration.ofSeconds(CODE_TTL_SECONDS));
            }
            if (failCount != null && failCount >= FAIL_LIMIT) {
                // 作废验证码，用户必须重新获取才能继续尝试
                stringRedisTemplate.delete(codeKey);
                log.warn("邮箱验证码连续校验失败已达上限，已作废该验证码，codeKey={}", codeKey);
            }
        } catch (Exception e) {
            log.error("邮箱验证码错误计数失败（不影响主流程），failKey={}", failKey, e);
        }
    }

    /**
     * 读取计数器当前值。
     *
     * @param key Redis key
     * @return 计数值；key 不存在或值非法时返回 0
     */
    private Long readCounter(String key) {
        String value = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(value)){
            return 0L;
        }
        try {
            return Long.parseLong(value);
        }catch (NumberFormatException e){
            // key 被外部写入非法值时按 0 处理，不阻断正常发信
            log.warn("邮箱验证码计数器值非法，按 0 处理，key={}, value={}", key, value);
            return 0L;
        }
    }

    /**
     * 日计数器自增，并在首次创建时把 TTL 设为「到当日 24:00」。
     *
     * <p><b>TTL 陷阱</b>：{@code INCR} 对已存在的 key 不会刷新 TTL，
     * 因此只在返回 1（首次创建）时设置过期时间；否则每次自增都会续期，计数永不失效。</p>
     */
    private void incrementDailyCounter(String key){
        Long value = stringRedisTemplate.opsForValue().increment(key);
        if (value != null && value == 1L) {
            stringRedisTemplate.expire(key, secondsUntilEndOfDay());
        }
    }
    /**
     * 计算距离当日 24:00 的剩余时长。
     *
     * @return 剩余时长，最小 1 秒（避免 23:59:59 附近算出 0 导致 key 立即失效）
     */
    private Duration secondsUntilEndOfDay() {
        long seconds = (DateUtil.endOfDay(new Date()).getTime() - System.currentTimeMillis()) / 1000L;
        return Duration.ofSeconds(Math.max(seconds, 1L));
    }

    /**
     * 生成 6 位数字验证码。
     * <p>用 {@code %06d} 补零，保证 {@code 000123} 这类值格式正确。</p>
     *
     * @return 6 位数字字符串
     */
    private String generateCode() {
        return String.format("%06d", SECURE_RANDOM.nextInt(CODE_BOUND));
    }

}
