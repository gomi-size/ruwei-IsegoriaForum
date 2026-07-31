package com.ruwei.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户查询条件（入参 DTO）。
 */
@Data
public class UserFollowOrFansPageDTO implements Serializable {

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
     * 内部主键 id（雪花 ASSIGN_ID），精确匹配
     */
    private Long id;

}
