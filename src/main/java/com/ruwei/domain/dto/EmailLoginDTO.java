package com.ruwei.domain.dto;

import lombok.Data;

/**
 * 邮箱验证码登录的请求类。
 *
 * @author Administrator
 */
@Data
public class EmailLoginDTO {

    /**
     * 账号绑定的邮箱
     */
    private String email;

    /**
     * 邮箱验证码（场景固定为 {@code EmailScene.LOGIN}）
     */
    private String code;


}