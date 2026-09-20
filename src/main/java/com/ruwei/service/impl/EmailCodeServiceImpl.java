package com.ruwei.service.impl;

import cn.hutool.core.util.StrUtil;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.EmailScene;
import com.ruwei.domain.constant.EmailCodeKeys;
import com.ruwei.exception.BusinessException;
import com.ruwei.service.EmailCodeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;

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
     * 校验错误次数上限：达到即作废验证码，强制用户重新获取。
     * <p>6 位数字共 100 万种组合，5 次尝试的命中概率约百万分之五，足以抵御暴力猜测。</p>
     */
    private static final long FAIL_LIMIT = 5L;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

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
}
