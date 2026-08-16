package com.ruwei.es.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * es的消息类
 */
@Getter
public class PostIndexEvent extends ApplicationEvent {

    /**
     * INDEX:就是查询
     * DELETE:就是删除
     */
    public enum Action { INDEX, DELETE }

    private final Long postId;
    private final Action action;

    public PostIndexEvent(Object source, Long postId, Action action) {
        super(source);
        this.postId = postId;
        this.action = action;
    }
}