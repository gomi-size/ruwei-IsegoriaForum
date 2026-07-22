package com.ruwei.domain.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private String userId;      // 对外展示的唯一编码

    private String username;    // 登录名

    private String nickname;    // 昵称

    private String avatar;      // 头像URL

    private Integer gender;     // 0未知 1男 2女

    private LocalDate birthday; // 生日

    private String bio;         // 个性签名

    private String location;    // 所在地

    private Integer level;      // 等级

    private Integer exp;        // 经验值

    private Integer followCount;// 关注数

    private Integer fansCount;  // 粉丝数

    private Integer postCount;  // 发帖数

    private LocalDateTime createdAt; // 注册时间

}