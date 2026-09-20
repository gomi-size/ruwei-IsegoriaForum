package com.ruwei.manager;

import com.ruwei.config.MailProperties;
import com.ruwei.domain.Enum.EmailScene;
import jakarta.annotation.Resource;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * {@link MailSender} 的异步实现：投递到专用线程池，接口线程不阻塞在 SMTP 握手上。
 *
 * <p><b>三个必须避开的 {@code @Async} 坑（本类已规避）</b>：</p>
 * <ol>
 *   <li><b>自调用失效</b>：{@code @Async} 依赖 Spring 代理，同类内部调用不走代理。
 *       因此本类**必须独立成 Bean**，由 {@code EmailCodeServiceImpl} 注入后调用；
 *       不能把 {@code sendVerifyCode} 写在 {@code EmailCodeServiceImpl} 里自己调自己。</li>
 *   <li><b>默认线程池</b>：不指定 executor 时会落到 Spring Boot 的
 *       {@code applicationTaskExecutor}，与其它异步任务共享队列。
 *       这里用 {@code @Async("mailExecutor")} 显式绑定专用池（见 {@code AsyncConfig}）。</li>
 *   <li><b>异常静默</b>：{@code @Async} 方法抛出的异常不会回传调用方，
 *       只会打到日志。所以方法体内必须 try-catch 全包，否则发信失败调用方完全无感。</li>
 * </ol>
 *
 * @author Administrator
 */
@Slf4j
@Component
public class AsyncMailSender implements MailSender {

    /**
     * 发件地址：直接复用 {@code spring.mail.username}，保证与 SMTP 信封地址一致。
     * <p>QQ 邮箱会校验 {@code MAIL FROM} 与邮件头 {@code From} 是否一致，
     * 不一致会直接返回 {@code 550}。用同一个配置项即可天然满足。</p>
     */
    @Value("${spring.mail.username:}")
    private String fromAddress;

    @Resource
    private JavaMailSender javaMailSender;

    @Resource
    private MailTemplateManager mailTemplateManager;

    @Resource
    private MailProperties mailProperties;

    /**
     * 发送验证码邮件（异步）。
     *
     * @param to    收件人邮箱
     * @param code  验证码明文
     * @param scene 使用场景
     */
    @Override
    @Async("mailExecutor")
    public void sendVerifyCode(String to, String code, EmailScene scene) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            // true 表示 multipart，用于同时携带纯文本与 HTML 两个版本
            MimeMessageHelper helper =
                    new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromAddress, mailProperties.getFromName());
            helper.setTo(to);
            helper.setSubject(mailTemplateManager.buildSubject(scene));
            helper.setText(
                    mailTemplateManager.renderVerifyCodePlain(code, scene, mailProperties.getCodeTtlSeconds()),
                    mailTemplateManager.renderVerifyCodeHtml(code, scene, mailProperties.getCodeTtlSeconds()));
            javaMailSender.send(message);
            log.info("验证码邮件发送成功，to={}, scene={}", to, scene.getCode());
        } catch (Exception e) {
            // 必须吞掉异常：@Async 抛出的异常不会回传调用方，不吞就等于无记录地失败
            log.error("验证码邮件发送失败，to={}, scene={}", to, scene.getCode(), e);
        }
    }
}