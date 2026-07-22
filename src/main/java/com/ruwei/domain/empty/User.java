package com.ruwei.domain.empty;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;

@TableName("user")
public class User  {
    private String userId;
    private String username;
    private String nickname;
    private String password;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private String bio;
    private String location;
    private Integer level;
    private Integer exp;
    private String phone;
    private String email;
    private Integer status;
    private Integer followCount;
    private Integer fansCount;
    private Integer postCount;
    // getters/setters
}
