package com.ruwei.component.notification.listener;

import com.ruwei.component.notification.event.AdminEvent;
import com.ruwei.component.notification.event.PostEvent;
import com.ruwei.domain.dto.SendNotificationDTO;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.User;
import com.ruwei.service.NotificationService;
import com.ruwei.service.PostService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static java.time.format.DateTimeFormatter.ofPattern;


/**
 * 管理员审向用户发送的消息
 */
@Slf4j
@Component
public class AdminEventListener {


    private final NotificationService notificationService;

    public AdminEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostAudited(AdminEvent event) {

        Long adminId = event.getAdminId();          // 管理员
        Long userId = event.getUserId();            // 用户
        String message = event.getMessage();// 消息
        if (adminId == null || userId == null ) {
            return;
        }
        String time = LocalDateTime.now().format(ofPattern("yyyy-MM-dd HH:mm:ss"));
        String content="系统在："+time+":"+message;

        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(userId);
        dto.setSenderId(adminId);
        dto.setType(6);
        dto.setTargetType(1);
        dto.setTargetId(userId);
        dto.setContent(content);
        dto.setBizKey(null);

        notificationService.sendNotification(dto);
    }
}
