package com.ruwei.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 通知分页查询条件（入参 DTO）。
 */
@Data
public class NotificationQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，从 1 开始，默认 1
     */
    private long current = 1;

    /**
     * 每页条数，默认 10
     */
    private long pageSize = 10;


    /**
     * 1点赞 2评论 3回复 4关注 5@提及 6系统 7收藏 8转发
     */
    private Integer type;
}
