package com.ruwei.component.notification.listener;

import com.ruwei.component.notification.event.ShareEvent;
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

import static java.time.format.DateTimeFormatter.ofPattern;

/**
 * 监听 ShareEvent（@Async + AFTER_COMMIT）：站内分享在事务提交后生成 type=8 通知落库 + WS 推送。
 * 文案「xxx 分享了一个帖子给你」；bizKey 按天幂等，同一人同一天分享同一帖给同一接收者只通知一次。
 */
@Slf4j
@Component
public class ShareEventListener {

    @Resource
    private UserService userService;

    private final NotificationService notificationService;

    public ShareEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShare(ShareEvent event) {
        Long actorId = event.getActorId();
        Long postId = event.getPostId();
        Long targetUserId = event.getTargetUserId();

        // 分享者可能已被删除/注销，防御性跳过
        User actor = userService.getById(actorId);
        if (actor == null) {
            return;
        }

        String todayStr = LocalDate.now().format(ofPattern("yyyyMMdd"));
        // 按天幂等：同一 (actor, target, post) 分享每天只通知一次（防重复骚扰）
        String bizKey = "share:" + actorId + ":" + targetUserId + ":" + postId + ":" + todayStr;
        String content = actor.getNickname() + "分享了一个帖子给你";

        // type=8 转发/分享；targetType=1 帖子（前端跳帖子详情）；targetId=帖子内部 id
        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(targetUserId);
        dto.setSenderId(actorId);
        dto.setType(8);
        dto.setTargetType(1);
        dto.setTargetId(postId);
        dto.setContent(content);
        dto.setBizKey(bizKey);
        notificationService.sendNotification(dto);   // 幂等：同 bizKey 已存在则跳过
    }
}
