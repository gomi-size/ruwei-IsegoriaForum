package com.ruwei.domain.dto;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 贴吧板块查询条件（入参 DTO）。
 *
 * <p>用于板块列表搜索，配合 {@code QueryWrapperUtils.getBoardQueryWrapper} 使用。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *     <li>{@link #name}：吧名，模糊匹配</li>
 *     <li>{@link #slug}：唯一标识，精确匹配</li>
 *     <li>{@link #description}：简介，模糊匹配</li>
 *     <li>{@link #creatorId}：创建者/吧主内部 id，精确匹配</li>
 *     <li>{@link #current} / {@link #pageSize}：分页参数</li>
 *     <li>{@link #sortField} / {@link #sortOrder}：排序字段与方向</li>
 * </ul>
 */
@Data
public class BoardQueryDTO implements Serializable {

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
     * 吧名，模糊匹配
     */
    private String name;

    /**
     * 唯一标识，精确匹配
     */
    private String slug;

    /**
     * 简介，模糊匹配
     */
    private String description;

    /**
     * 创建者/吧主内部 id，精确匹配
     */
    private Long creatorId;

    /**
     * 排序字段（对应数据库驼峰列名，如 followCount / postCount / createdAt / name），为空则按关注数倒序
     */
    private String sortField;

    /**
     * 排序方向：{@code ascend} = 升序，其它或空 = 降序
     */
    private String sortOrder;
}
