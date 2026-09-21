package com.ruwei.domain.dto;

import lombok.Data;

/**
 * 绑定（换绑）邮箱请求体（入参 DTO）。
 *
 * <p>用于已登录用户 {@code POST /user/bindEmail}：新邮箱 + 该邮箱收到的
 * {@code EmailScene.BIND_EMAIL} 场景验证码。换绑成功后旧邮箱立即失效
 * （验证码登录、找回密码均按 user.email 当前值定位账号）。</p>
 *
 * @author Administrator
 */
@Data
public class EmailBindDTO {

    /** 新邮箱（必填，通用邮箱格式） */
    private String email;

    /** 新邮箱收到的验证码（场景 bindEmail，必填） */
    private String code;
}
