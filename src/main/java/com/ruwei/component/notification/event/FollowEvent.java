package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;


/**
 *发送消息的载体
 */
@Getter
public class FollowEvent extends ApplicationEvent {

    /** 触发者（主动关注方）内部 id */
    private final Long actorId;
    /** 被关注者（接收通知方）内部 id */
    private final Long followeeId;

    public FollowEvent(Object source, Long actorId, Long followeeId) {
        super(source);
        this.actorId = actorId;
        this.followeeId = followeeId;
    }
}