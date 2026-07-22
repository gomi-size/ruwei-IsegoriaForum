package com.ruwei.domain.dto;

import lombok.Data;


/**
 * 用户注册的请求类
 */
@Data
public class UserRegisterDTO  {

    private String username;

    private String password;

    private String nickname;
}