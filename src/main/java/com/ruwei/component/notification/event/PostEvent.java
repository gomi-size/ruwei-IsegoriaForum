package com.ruwei.component.notification.event;

import com.ruwei.domain.vo.UserVO;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.util.List;

/**
 * 发送帖子的消息消息类
 */
@Getter
public class PostEvent extends ApplicationEvent {

    /** 发帖人内部 id */
    private final Long actorId;

    /** 被关注的板块内部 id */
    private final Long PostId;

    /**
     * 粉丝的id
     */
    private final List<Long> followList ;

    public PostEvent(Object source, Long actorId, Long PostId,List<Long> followList) {
        super(source);
        this.actorId = actorId;
        this.PostId = PostId;
        this.followList=followList;
    }
}
