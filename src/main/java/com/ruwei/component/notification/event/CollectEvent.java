package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 收藏行为事件载体：收藏（collected=true）与取消收藏（collected=false）均发布（仅在 DB 状态真实变更后），
 * 只服务用户兴趣画像（收藏为私密行为，不通知作者）。
 * 监听端：RecInterestListener#onCollect（@Async + AFTER_COMMIT + fallbackExecution，写行为 + 短期兴趣 ±2.0）。
 */
@Getter
public class CollectEvent extends ApplicationEvent {

    /** 操作者（收藏/取消者）内部 id */
    private final Long actorId;
    /** 帖子内部 id */
    private final Long postId;
    /** true=收藏 / false=取消收藏（取消供画像 -2.0 对称降权） */
    private final boolean collected;

    public CollectEvent(Object source, Long actorId, Long postId, boolean collected) {
        super(source);
        this.actorId = actorId;
        this.postId = postId;
        this.collected = collected;
    }
}
