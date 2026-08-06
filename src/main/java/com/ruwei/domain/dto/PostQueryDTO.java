package com.ruwei.domain.dto;

import com.ruwei.common.PageRequest;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.io.Serializable;

/**
 * 帖子分页查询条件（入参 DTO）。
 *
 * <p>用于帖子列表页：可查自己的帖子、也可查别人的帖子；
 * 字符串字段（postCode / title / createdAt）模糊匹配，id 类字段（id / boardId / userId）精确匹配；
 * 均不传则查询全部，默认按创建时间倒序（最新在前）。分页/排序参数继承自 {@link PageRequest}。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PostQueryDTO extends PageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 帖子内部主键（精确匹配）
     */
    private Long postId;

    /**
     * 对外唯一编码（模糊匹配，支持输入部分编码）
     */
    private String postCode;

    /**
     * 所属板块内部 id（精确匹配）
     */
    private Long boardId;

    /**
     * 标题（模糊匹配）
     */
    private String title;

    /**
     * 作者内部 id（精确匹配；查他人时只返回已发布帖子）
     */
    private Long userId;

    /**
     * 创建时间（模糊匹配，datetime 列 LIKE 匹配，传 "2026-08-05" 可查当天）
     */
    private String createdAt;
}
