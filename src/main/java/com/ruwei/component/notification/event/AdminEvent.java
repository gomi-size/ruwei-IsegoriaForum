package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;


/**
 *管理员向用户发送的消息载体
 */
@Getter
public class AdminEvent extends ApplicationEvent {

    /** 触发者（主动关注方）内部 id */
    private final Long adminId;
    /** 被关注者（接收通知方）内部 id */
    private final Long userId;
    /**消息**/
    private final String message;

    public AdminEvent(Object source, Long adminId, Long userId, String message) {
        super(source);
        this.adminId = adminId;
        this.userId = userId;
        this.message = message;
    }

}