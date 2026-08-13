package com.ruwei.component.notification.Publisher;

import com.ruwei.domain.dto.NotifyPushMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 本地实现：通过 WebSocket(STOMP) 直接推送给在线用户。
 * convertAndSendToUser(内部id, "/queue/notify", msg) —— Spring 按 Principal.getName()=内部id 路由到该用户全部会话。
 * 消费者
 */
@Slf4j
@Component
public class LocalNotificationPublisher implements NotificationPublisher {

    private static final String DEST = "/queue/notify";

    /**
     * 这个地城就是websocket
     */
    private final SimpMessagingTemplate messagingTemplate;

    public LocalNotificationPublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void push(Long internalId, NotifyPushMessage message) {
        try {
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(internalId), DEST, message);
        } catch (Exception e) {
            // 用户不在线或会话已断：静默失败，前端上线后从历史表拉取，不抛异常
            log.debug("WS push skipped (user offline?), internalId={}", internalId, e);
        }
    }
}