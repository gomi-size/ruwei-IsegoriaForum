package com.ruwei.service;

import com.ruwei.domain.Enum.EmailScene;

/**
 * 邮箱验证码服务：负责验证码的生成、投递、校验与消费。
 *
 * <p>验证码只存 Redis、不落库（详见 docs/modules/15-email-code-module.md §3.1）。</p>
 *
 * @author Administrator
 */
public interface EmailCodeService {

    /**
     * 校验并消费验证码（一次性）。
     *
     * <p>三态判定逻辑：</p>
     * <ul>
     *   <li><b>无 key</b>：验证码不存在或已过期 → 抛 {@code EMAIL_CODE_ERROR}</li>
     *   <li><b>不匹配</b>：错误计数 +1，累计达 5 次作废验证码 → 抛 {@code EMAIL_CODE_ERROR}</li>
     *   <li><b>匹配</b>：原子消费（{@code DEL} 返回 true 者才获得消费权）后放行</li>
     * </ul>
     *
     * <p><b>并发安全</b>：多个请求携带同一验证码时，只有 {@code DEL} 返回 {@code true}
     * 的那一个能通过 —— Redis {@code DEL} 在单线程内原子执行，其返回值天然构成一次 CAS，
     * 无需额外加锁或 Lua 脚本。</p>
     *
     * @param email 目标邮箱
     * @param scene 使用场景（决定 key 命名空间，场景间验证码不互通）
     * @param code  用户提交的验证码明文
     */
    void consumeCode(String email, EmailScene scene, String code);
}
