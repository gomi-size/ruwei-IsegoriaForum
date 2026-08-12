package com.ruwei.domain.dto;

import lombok.Data;

/**
 * 管理员设置帖子状态/可见性的请求体。
 * status / visibility 传枚举码，传哪个改哪个，至少传一个；null 表示不修改该字段。
 */
@Data
public class AdminPostStatusDTO {

    /**
     * 帖子 id
     */
    private Long postId;

    /**
     * 帖子状态码：1已发布 2草稿 3审核中 4下架；null 表示不修改
     */
    private Integer status;

    /**
     * 可见性码：1公开 2仅粉丝可见 3私密；null 表示不修改
     */
    private Integer visibility;
}
