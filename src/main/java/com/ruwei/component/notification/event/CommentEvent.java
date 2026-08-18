package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 一级评论事件：用户在帖子下发评论（parentId=0）时，于事务提交后由
 * {@link com.ruwei.component.notification.listener.CommentEventListener} 消费，
 * 生成通知推送给<b>帖子作者</b>（type=2 评论）。
 */
@Getter
public class CommentEvent extends ApplicationEvent {

    /** 帖子内部 id（通知跳转目标帖） */
    private final Long postId;

    /** 评论内部 id（通知跳转锚点，对应 notification.commentId） */
    private final Long commentId;

    /** 评论者（主动评论方）内部 id */
    private final Long commentUserId;

    /** 帖子作者（接收通知方）内部 id */
    private final Long postUserId;

    /** 评论内容摘要（预览文案用） */
    private final String content;

    public CommentEvent(Object source, Long postId, Long commentId,
                        Long commentUserId, Long postUserId, String content) {
        super(source);
        this.postId = postId;
        this.commentId = commentId;
        this.commentUserId = commentUserId;
        this.postUserId = postUserId;
        this.content = content;
    }
}
