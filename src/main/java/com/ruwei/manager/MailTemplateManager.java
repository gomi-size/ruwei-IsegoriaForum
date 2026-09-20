package com.ruwei.manager;

import com.ruwei.domain.Enum.EmailScene;
import org.springframework.stereotype.Component;

/**
 * 邮件内容渲染。
 *
 * <p>同时产出 HTML 与纯文本两个版本：{@code MimeMessageHelper.setText(plain, html)}
 * 会生成 {@code multipart/alternative} 邮件，纯文本兜底能显著降低被判垃圾邮件的概率。</p>
 *
 * @author Administrator
 */
@Component
public class MailTemplateManager {

    /**
     * 站点名，出现在邮件标题前缀与正文标题。
     */
    private static final String SITE_NAME = "Isegoria 论坛";

    /**
     * HTML 模板。
     *
     * <p>占位符按 {@link String#format} 顺序为：站点名、场景描述、验证码、有效期（分钟）。</p>
     * <p><b>注意</b>：正文中不可出现裸 {@code %} 字符，否则会被 {@code String.format} 当作格式符解析并抛异常。</p>
     */
    private static final String VERIFY_CODE_HTML = """
            <div style="max-width:520px;margin:0 auto;padding:24px;font-family:'Helvetica Neue',Arial,'PingFang SC','Microsoft YaHei',sans-serif;color:#1f2937;">
              <h2 style="margin:0 0 16px;font-size:18px;font-weight:600;">%s</h2>
              <p style="margin:0 0 8px;font-size:14px;line-height:1.6;">你正在进行「%s」操作，验证码为：</p>
              <div style="margin:16px 0;padding:18px 16px;background:#f3f4f6;border-radius:8px;text-align:center;">
                <span style="font-size:32px;font-weight:700;letter-spacing:6px;color:#111827;">%s</span>
              </div>
              <p style="margin:0 0 8px;font-size:13px;color:#6b7280;">验证码 %d 分钟内有效，请勿泄露给他人。</p>
              <p style="margin:0;font-size:13px;color:#6b7280;">如非本人操作，忽略本邮件即可，你的账号不会受到影响。</p>
            </div>
            """;

    /**
     * 渲染验证码邮件的 HTML 正文。
     *
     * @param code       验证码明文
     * @param scene      使用场景
     * @param ttlSeconds 有效期（秒）
     * @return HTML 正文
     */
    public String renderVerifyCodeHtml(String code, EmailScene scene, long ttlSeconds) {
        return String.format(VERIFY_CODE_HTML, SITE_NAME, scene.getDesc(), code, toMinutes(ttlSeconds));
    }

    /**
     * 渲染验证码邮件的纯文本正文（反垃圾兜底）。
     *
     * @param code       验证码明文
     * @param scene      使用场景
     * @param ttlSeconds 有效期（秒）
     * @return 纯文本正文
     */
    public String renderVerifyCodePlain(String code, EmailScene scene, long ttlSeconds) {
        return "【" + SITE_NAME + "】你正在进行「" + scene.getDesc() + "」操作，验证码是 " + code
                + "，" + toMinutes(ttlSeconds) + " 分钟内有效。请勿泄露给他人；如非本人操作请忽略本邮件。";
    }

    /**
     * 构建邮件标题。
     *
     * @param scene 使用场景
     * @return 形如「【Isegoria 论坛】注册验证码」
     */
    public String buildSubject(EmailScene scene) {
        return "【" + SITE_NAME + "】" + scene.getDesc() + "验证码";
    }

    /**
     * 秒转分钟，向上取整且最小为 1（避免 300 秒显示成 5 以外的奇怪值）。
     *
     * @param ttlSeconds 有效期（秒）
     * @return 分钟数
     */
    private long toMinutes(long ttlSeconds) {
        return Math.max(1L, (ttlSeconds + 59L) / 60L);
    }
}