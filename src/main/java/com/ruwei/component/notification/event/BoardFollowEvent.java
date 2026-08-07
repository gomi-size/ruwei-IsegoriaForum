package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 板块关注事件：用户关注板块成功后发布，由 {@code BoardFollowEventListener} 在事务提交后
 * 生成通知落库并实时推送给板块创建者（吧主）。
 *
 * <p>与 {@link FollowEvent}（用户关注用户）区分：接收方是板块创建者 {@link #ownerId}，
 * 关联对象是板块 {@link #boardId}，通知 {@code targetType=3}（板块）。</p>
 */
@Getter
public class BoardFollowEvent extends ApplicationEvent {

    /** 主动关注者（触发者）内部 id */
    private final Long actorId;

    /** 被关注的板块内部 id */
    private final Long boardId;

    /** 板块创建者（接收通知方）内部 id */
    private final Long ownerId;

    public BoardFollowEvent(Object source, Long actorId, Long boardId, Long ownerId) {
        super(source);
        this.actorId = actorId;
        this.boardId = boardId;
        this.ownerId = ownerId;
    }

}
