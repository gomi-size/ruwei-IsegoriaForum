package com.ruwei.component.notification.listener;

import com.ruwei.component.notification.event.FollowEvent;
import com.ruwei.domain.dto.SendNotificationDTO;
import com.ruwei.domain.empty.User;
import com.ruwei.service.NotificationService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 监听 FollowEvent：在关注事务提交后，生成通知落库（幂等），并实时推送给被关注者。
 * 落库 + 推送复用 {@link NotificationService#sendNotification} 公共写入方法。
 */
@Slf4j
@Component
public class FollowUserEventListener {

    @Resource
    private UserService userService;

    private final NotificationService notificationService;

    public FollowUserEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollow(FollowEvent event) {
        Long actorId = event.getActorId();      // 主动关注方（内部 id）
        Long followeeId = event.getFolloweeId(); // 被关注方（内部 id）

        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // ① 幂等：同一 (actor, followee) 关注只产生一条通知（每天一次）
        String bizKey = "follow:" + actorId + ":" + followeeId + ":" + todayStr;

        // 关注者可能已被删除/注销，防御性跳过
        User user = userService.getById(actorId);
        if (user == null) {
            return;
        }

        String content = user.getNickname() + "在" + todayStr + "时间，关注了你";

        // type=4 关注；targetType=2 用户；targetId=被关注者内部 id（前端跳其主页）
        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(followeeId);
        dto.setSenderId(actorId);
        dto.setType(4);
        dto.setTargetType(2);
        dto.setTargetId(followeeId);
        dto.setContent(content);
        dto.setBizKey(bizKey);
        notificationService.sendNotification(dto);
    }
}
