package com.ruwei.domain.dto;

import lombok.Data;


/**
 * 用户注册的请求类
 */
@Data
public class UserRegisterDTO  {

    /**
     *用户名
     */
    private String username;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 邮箱验证码
     */
    private String code;

    /**
     * 密码
     */
    private String password;

    /**
     * 密码
     */
    private String checkPassword;


}