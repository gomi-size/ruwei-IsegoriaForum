package com.ruwei.manager;

import com.ruwei.domain.Enum.EmailScene;

/**
 * 邮件发送能力抽象。
 *
 * <p>业务侧只依赖本接口，不感知具体投递方式：当前实现为
 * {@link AsyncMailSender}（{@code @Async} 线程池直连 SMTP）；
 * 若后续按 {@code docs/features/P2-外围服务拆分.md} 把邮件抽为独立服务，
 * 只需新增一个 {@code MqMailSender} 实现并替换 Bean，业务代码零改动。</p>
 *
 * <p><b>失败静默</b>：实现方内部捕获全部异常并记 ERROR 日志，不向上抛 ——
 * 验证码是「用户可自行重试」的场景，发信失败不应让发码接口报错。</p>
 *
 * @author Administrator
 */
public interface MailSender {

    /**
     * 发送验证码邮件。
     *
     * @param to    收件人邮箱
     * @param code  验证码明文
     * @param scene 使用场景（决定邮件标题与正文文案）
     */
    void sendVerifyCode(String to, String code, EmailScene scene);
}