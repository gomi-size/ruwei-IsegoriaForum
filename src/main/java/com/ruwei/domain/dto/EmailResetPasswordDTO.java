package com.ruwei.domain.dto;

import lombok.Data;

/**
 * 凭邮箱验证码重置密码的请求类（未登录场景）。
 *
 * <p>对应原 {@code /user/forgetPassword} 接口的入参重构：原实现仅凭 {@code userId}
 * 即可重置任意账号密码，且写入的是明文密码，已整体废弃。</p>
 *
 * @author Administrator
 */
@Data
public class EmailResetPasswordDTO {

    /**
     * 账号绑定的邮箱（同时作为身份凭证的一部分）
     */
    private String email;

    /**
     * 邮箱验证码（场景固定为 {@code EmailScene.RESET_PASSWORD}）
     */
    private String code;

    /**
     * 新密码
     */
    private String password;

    /**
     * 确认新密码
     */
    private String checkPassword;
}
