package com.ruwei.domain.dto;

import lombok.Data;

@Data
public class CommentAddDTO {
    private String postCode;      // 必填, 帖子对外编码 → 解析内部 postId
    private String content;       // 必填, trim 后非空, ≤1000
    private Long parentId;        // 可选, 0/缺省=一级评论
    private Long replyToUserId;   // 可选, 二级时缺省自动取父评论作者
}
