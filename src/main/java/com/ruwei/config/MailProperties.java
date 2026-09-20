package com.ruwei.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 邮箱验证码业务参数（对应 application-local.yml 的 {@code isegoria.mail} 段）。
 *
 * <p>与 {@code spring.mail.*}（SMTP 连接参数）区分开：本类只放业务侧阈值，
 * 便于在不重启改 SMTP 的前提下单独调整限流与有效期。</p>
 *
 * @author Administrator
 */
@Configuration
@ConfigurationProperties(prefix = "isegoria.mail")
public class MailProperties {

    /** 发件人显示名（计入邮件头 From 的 display name，不影响 SMTP 信封地址） */
    private String fromName = "Isegoria 论坛";

    /** 验证码有效期（秒） */
    private long codeTtlSeconds = 300L;

    /** 同一邮箱再次发码的冷却时间（秒） */
    private long cooldownSeconds = 60L;

    /** 同一邮箱每日发码上限 */
    private int dailyLimitPerEmail = 10;

    /** 同一 IP 每日发码上限 */
    private int dailyLimitPerIp = 50;

    /** 单条验证码允许的最大校验失败次数，达到即作废 */
    private long failLimit = 5L;

    public String getFromName() {
        return fromName;
    }

    public void setFromName(String fromName) {
        this.fromName = fromName;
    }

    public long getCodeTtlSeconds() {
        return codeTtlSeconds;
    }

    public void setCodeTtlSeconds(long codeTtlSeconds) {
        this.codeTtlSeconds = codeTtlSeconds;
    }

    public long getCooldownSeconds() {
        return cooldownSeconds;
    }

    public void setCooldownSeconds(long cooldownSeconds) {
        this.cooldownSeconds = cooldownSeconds;
    }

    public int getDailyLimitPerEmail() {
        return dailyLimitPerEmail;
    }

    public void setDailyLimitPerEmail(int dailyLimitPerEmail) {
        this.dailyLimitPerEmail = dailyLimitPerEmail;
    }

    public int getDailyLimitPerIp() {
        return dailyLimitPerIp;
    }

    public void setDailyLimitPerIp(int dailyLimitPerIp) {
        this.dailyLimitPerIp = dailyLimitPerIp;
    }

    public long getFailLimit() {
        return failLimit;
    }

    public void setFailLimit(long failLimit) {
        this.failLimit = failLimit;
    }
}