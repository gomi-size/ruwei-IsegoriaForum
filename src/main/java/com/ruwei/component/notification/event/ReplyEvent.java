package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 二级回复事件：用户在某条评论下回复（parentId&gt;0，含楼中楼互评）时，
 * 于事务提交后由 {@link com.ruwei.component.notification.listener.CommentEventListener} 消费，
 * 生成通知推送给<b>被回复者 replyToUserId</b>（type=3 回复）。
 */
@Getter
public class ReplyEvent extends ApplicationEvent {

    /** 帖子内部 id（通知跳转目标帖） */
    private final Long postId;

    /** 回复（二级评论）内部 id（通知跳转锚点，对应 notification.commentId） */
    private final Long commentId;

    /** 回复者内部 id */
    private final Long commentUserId;

    /** 被回复者内部 id（接收通知方） */
    private final Long replyToUserId;

    /** 帖子作者内部 id（用于去重：回复者回的若是帖主的楼中楼，只发 type=3 即可） */
    private final Long postUserId;

    /** 回复内容摘要（预览文案用） */
    private final String content;

    public ReplyEvent(Object source, Long postId, Long commentId,
                      Long commentUserId, Long replyToUserId, Long postUserId, String content) {
        super(source);
        this.postId = postId;
        this.commentId = commentId;
        this.commentUserId = commentUserId;
        this.replyToUserId = replyToUserId;
        this.postUserId = postUserId;
        this.content = content;
    }
}
