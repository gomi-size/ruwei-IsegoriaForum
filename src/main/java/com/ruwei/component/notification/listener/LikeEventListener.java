package com.ruwei.component.notification.listener;

import com.ruwei.component.notification.event.LikeEvent;
import com.ruwei.domain.dto.SendNotificationDTO;
import com.ruwei.domain.empty.User;
import com.ruwei.service.NotificationService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 LikeEvent（@Async，不走 AFTER_COMMIT——点赞 Service 无 DB 事务）：
 * 生成 type=1 点赞通知落库 + WS 实时推送（幂等由 bizKey 保证）。
 * 文案「xxx 赞了你的帖子」；bizKey = like:post:{actorId}:{postId}（同动作重复点赞不重复通知）。
 */
@Slf4j
@Component
public class LikeEventListener {

    @Resource
    private UserService userService;
    @Resource
    private NotificationService notificationService;

    @Async("eventTaskExecutor")
    @TransactionalEventListener        // 此处无事务，等价于普通 @EventListener + 异步执行
    public void onLike(LikeEvent event) {
        Long actorId = event.getActorId();
        Long postId = event.getPostId();
        Long postUserId = event.getPostUserId();

        // 自己赞自己不发（11 §10 去重）
        if (actorId.equals(postUserId)) {
            return;
        }
        User actor = userService.getById(actorId);
        if (actor == null) {
            return;
        }
        String bizKey = "like:post:" + actorId + ":" + postId;   // 幂等键（对齐 notification.uk_biz_key）
        String content = actor.getNickname() + "赞了你的帖子";

        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(postUserId);
        dto.setSenderId(actorId);
        dto.setType(1);              // 1点赞
        dto.setTargetType(1);        // 1帖子
        dto.setTargetId(postId);
        dto.setContent(content);
        dto.setBizKey(bizKey);
        notificationService.sendNotification(dto);   // 幂等：同 bizKey 已存在则跳过
    }
}