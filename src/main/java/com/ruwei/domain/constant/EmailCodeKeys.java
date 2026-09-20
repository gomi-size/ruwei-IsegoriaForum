package com.ruwei.domain.constant;

import com.ruwei.domain.Enum.EmailScene;

/**
 * 邮箱验证码相关 Redis key 常量与构建方法。
 *
 * <p>集中收口 key 拼接规则，禁止在业务代码中散落魔法字符串，
 * 避免拼写不一致导致「发了码却校验不到」这类隐蔽 bug。</p>
 *
 * <p>完整 key 一览（对齐 docs/modules/15-email-code-module.md §4）：</p>
 * <pre>
 * auth:email:code:{scene}:{email}      验证码本体，TTL 300s，校验通过后立即 DEL
 * auth:email:cooldown:{email}          单邮箱发送冷却，TTL 60s
 * auth:email:daily:email:{email}       单邮箱日发送计数，TTL 至当日 24:00
 * auth:email:daily:ip:{ip}             单 IP 日发送计数，TTL 至当日 24:00
 * auth:email:fail:{scene}:{email}      校验错误计数，≥5 次作废验证码
 * </pre>
 *
 * @author Administrator
 */
public final class EmailCodeKeys {

    /**
     * 工具类禁止实例化。
     */
    private EmailCodeKeys() {
    }

    /** 验证码本体前缀：auth:email:code:{scene}:{email} */
    public static final String CODE_PREFIX = "auth:email:code:";

    /** 单邮箱发送冷却前缀：auth:email:cooldown:{email} */
    public static final String COOLDOWN_PREFIX = "auth:email:cooldown:";

    /** 单邮箱日发送计数前缀：auth:email:daily:email:{email} */
    public static final String DAILY_EMAIL_PREFIX = "auth:email:daily:email:";

    /** 单 IP 日发送计数前缀：auth:email:daily:ip:{ip} */
    public static final String DAILY_IP_PREFIX = "auth:email:daily:ip:";

    /** 校验错误计数前缀：auth:email:fail:{scene}:{email} */
    public static final String FAIL_PREFIX = "auth:email:fail:";

    /**
     * 构建验证码本体 key。
     *
     * @param scene 使用场景
     * @param email 目标邮箱
     * @return 完整 Redis key
     */
    public static String codeKey(EmailScene scene, String email) {
        return CODE_PREFIX + scene.getCode() + ":" + email;
    }

    /**
     * 构建校验错误计数 key。
     *
     * @param scene 使用场景
     * @param email 目标邮箱
     * @return 完整 Redis key
     */
    public static String failKey(EmailScene scene, String email) {
        return FAIL_PREFIX + scene.getCode() + ":" + email;
    }

    /**
     * 构建单邮箱发送冷却 key。
     *
     * @param email 目标邮箱
     * @return 完整 Redis key
     */
    public static String cooldownKey(String email) {
        return COOLDOWN_PREFIX + email;
    }

    /**
     * 构建单邮箱日发送计数 key。
     *
     * @param email 目标邮箱
     * @return 完整 Redis key
     */
    public static String dailyEmailKey(String email) {
        return DAILY_EMAIL_PREFIX + email;
    }

    /**
     * 构建单 IP 日发送计数 key。
     *
     * @param clientIp 客户端 IP
     * @return 完整 Redis key
     */
    public static String dailyIpKey(String clientIp) {
        return DAILY_IP_PREFIX + clientIp;
    }

}
