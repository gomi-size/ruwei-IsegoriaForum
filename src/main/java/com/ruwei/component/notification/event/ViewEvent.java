package com.ruwei.component.notification.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;


/**
 * 这个是浏览记录的的消息
 */
@Getter
public class ViewEvent extends ApplicationEvent {
    private final Long userId;      // 浏览者内部 id（未登录的浏览不做处理）
    private final Long postId;      // 帖子内部 id
    private final String topic;     // 帖子 topic（逗号分隔标签名，供兴趣 INCR）
    private final Integer type;     // 帖子内容形态 1图文 2视频 3纯文
    private final Long boardId;     // 所属板块（可空）


    public ViewEvent(Object source,Long userId,Long postId,String topic,Integer type,Long boardId) {
        super(source);
        this.userId=userId;
        this.postId=postId;
        this.topic=topic;
        this.type=type;
        this.boardId=boardId;

    }

}