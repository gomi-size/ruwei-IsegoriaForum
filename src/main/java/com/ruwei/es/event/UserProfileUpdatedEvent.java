package com.ruwei.es.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 用户资料变更事件：触发该作者全部 ES 帖子的索引重建。
 *
 * <p>背景：{@code PostDoc} 冗余了作者昵称/头像（nickname/avatar），仅在帖子索引时快照；
 * 用户编辑资料（昵称/头像等）后若不重建，推荐流/搜索展示的仍是旧昵称头像。
 * 由 {@code UserProfileEventListener} 异步消费（@Async + AFTER_COMMIT fallback），
 * 调 {@code EsPostSyncService#reindexByAuthorId} 批量重建该作者应索引的帖子。</p>
 */
@Getter
public class UserProfileUpdatedEvent extends ApplicationEvent {

    /** 被编辑用户内部 id（管理员代改他人资料时 = 被改者 id） */
    private final Long userId;

    public UserProfileUpdatedEvent(Object source, Long userId) {
        super(source);
        this.userId = userId;
    }
}
