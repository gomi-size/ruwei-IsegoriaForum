package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 站内分享事件载体：仅「站内分享给指定用户」时发布（站外分享只计数+流水，不发事件、不通知）。
 * 监听端：ShareEventListener（@Async + AFTER_COMMIT 发 type=8 通知）。
 */
@Getter
public class ShareEvent extends ApplicationEvent {

    /** 分享者内部 id */
    private final Long actorId;
    /** 帖子内部 id */
    private final Long postId;
    /** 站内分享接收者内部 id */
    private final Long targetUserId;

    public ShareEvent(Object source, Long actorId, Long postId, Long targetUserId) {
        super(source);
        this.actorId = actorId;
        this.postId = postId;
        this.targetUserId = targetUserId;
    }
}
