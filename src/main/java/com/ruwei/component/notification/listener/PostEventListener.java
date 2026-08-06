package com.ruwei.component.notification.listener;

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
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 监听 {@link PostEvent}：管理员审核通过后，向该帖子<b>作者的粉丝</b>逐条发送通知
 * （新帖发布推送，仿照 {@link FollowUserEventListener} / {@link BoardFollowEventListener} 的模式）。
 *
 * <p>要点：
 * <ul>
 *   <li>{@code type=6}（系统通知：帖子发布推送）、{@code targetType=1}（帖子）、{@code targetId=帖子内部 id}
 *       （前端点击跳转帖子详情）；</li>
 *   <li>粉丝列表由事件在审核事务内携带（{@code followList}），此处遍历逐条落库+推送；</li>
 *   <li>幂等键 {@code postPublish:{actor}:{postId}:{fanId}:{yyyyMMdd}}——同一粉丝对同一帖每天只通知一次；</li>
 *   <li>作者/帖子在异步线程可能已被删除，防御性跳过。</li>
 * </ul>
 * 落库 + 推送复用 {@link NotificationService#sendNotification} 公共写入方法。</p>
 */
@Slf4j
@Component
public class PostEventListener {

    @Resource
    private UserService userService;

    @Resource
    private PostService postService;

    private final NotificationService notificationService;

    public PostEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostAudited(PostEvent event) {

        Long actorId = event.getActorId();          // 作者（内部 id）
        Long postId = event.getPostId();            // 帖子（内部 id）
        List<Long> followList = event.getFollowList(); // 作者粉丝（内部 id 列表）

        if (actorId == null || postId == null || followList == null || followList.isEmpty()) {
            return;
        }

        // 作者或帖子可能已被删除/下架，防御性跳过
        User user = userService.getById(actorId);
        Post post = postService.getById(postId);
        if (user == null || post == null) {
            return;
        }

        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        String content = user.getNickname() + "发布了新帖子《" + post.getTitle() + "》";

        // 逐粉丝发通知：幂等键含 fanId，保证每个粉丝每天一条
        for (Long fanId : followList) {
            String bizKey = "postPublish:" + actorId + ":" + postId + ":" + fanId + ":" + todayStr;

            SendNotificationDTO dto = new SendNotificationDTO();
            dto.setReceiverId(fanId);
            dto.setSenderId(actorId);
            dto.setType(6);         // 系统通知（帖子发布推送）
            dto.setTargetType(1);   // 帖子
            dto.setTargetId(postId);
            dto.setContent(content);
            dto.setBizKey(bizKey);
            notificationService.sendNotification(dto);
        }
    }


}
