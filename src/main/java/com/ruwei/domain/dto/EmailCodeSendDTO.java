package com.ruwei.domain.dto;

import lombok.Data;

/**
 * 发送邮箱验证码的请求类。
 *
 * @author Administrator
 */
@Data
public class EmailCodeSendDTO {

    /**
     * 目标邮箱
     */
    private String email;

    /**
     * 使用场景编码，取值见 {@code EmailScene}：
     * {@code register} / {@code login} / {@code resetPwd} / {@code bindEmail}
     */
    private String scene;
}