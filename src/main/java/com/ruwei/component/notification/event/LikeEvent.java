package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 点赞事件载体：仅 action=1（点赞）时发布；取赞不发、不删已发通知。
 * 监听端：LikeEventListener（@Async 发 type=1 通知）。
 */
@Getter
public class LikeEvent extends ApplicationEvent {

    /** 触发者（点赞者）内部 id */
    private final Long actorId;
    /** 帖子内部 id */
    private final Long postId;
    /** 被赞者（帖子作者）内部 id */
    private final Long postUserId;

    public LikeEvent(Object source, Long actorId, Long postId, Long postUserId) {
        super(source);
        this.actorId = actorId;
        this.postId = postId;
        this.postUserId = postUserId;
    }
}