package com.ruwei.component.notification.listener;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruwei.component.notification.NotificationPublisher;
import com.ruwei.component.notification.event.FollowEvent;
import com.ruwei.domain.dto.NotifyPushMessage;
import com.ruwei.domain.empty.Notification;

import com.ruwei.domain.empty.User;
import com.ruwei.mapper.NotificationMapper;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * 监听 FollowEvent：在关注事务提交后，生成通知落库（幂等），并实时推送给被关注者。
 */
@Slf4j
@Component
public class FollowEventListener {

    @Resource
    private UserService userService;

    private final NotificationMapper notificationMapper;
    private final NotificationPublisher publisher;

    public FollowEventListener(NotificationMapper notificationMapper, NotificationPublisher publisher) {
        this.notificationMapper = notificationMapper;
        this.publisher = publisher;
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollow(FollowEvent event) {
        Long actorId = event.getActorId();      // 主动关注方（内部 id）
        Long followeeId = event.getFolloweeId(); // 被关注方（内部 id）

        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        // ① 幂等：同一 (actor, followee) 关注只产生一条通知
        String bizKey = "follow:" + actorId + ":" + followeeId+":"+todayStr;
        if (notificationMapper.selectCount(
                new QueryWrapper<Notification>().eq("bizKey", bizKey)) > 0) {
            return;
        }

        User user = userService.getById(actorId);
        String nickname = user.getNickname();
        //保存到notification库中
        Notification n = new Notification();
        n.setReceiverId(followeeId);
        n.setSenderId(actorId);
        n.setType(4);                 // 4 = 关注
        n.setTargetType(2);           // 2 = 用户（指向被关注者主页）
        n.setTargetId(followeeId);    // 前端可据此跳转其主页
        n.setContent(nickname+"在"+todayStr+"时间，关注了你");
        n.setBizKey(bizKey);
        n.setIsRead(0);
        n.setCreatedAt(new Date());
        notificationMapper.insert(n);

        // 实时推送
        NotifyPushMessage push = new NotifyPushMessage();
        push.setNotificationId(n.getId());
        push.setReceiverId(followeeId);
        push.setType(4);
        push.setSenderId(actorId);
        push.setTargetType(2);
        push.setTargetId(followeeId);
        push.setContent(n.getContent());
        push.setCreatedAt(n.getCreatedAt().getTime());
        //通过WebSocket 发送给用户发送给对应的用户
        publisher.push(followeeId, push);
    }
}