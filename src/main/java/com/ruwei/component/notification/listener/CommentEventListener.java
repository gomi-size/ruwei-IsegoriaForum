package com.ruwei.component.notification.listener;

import cn.hutool.core.util.StrUtil;
import com.ruwei.component.notification.event.CommentEvent;
import com.ruwei.component.notification.event.ReplyEvent;
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

/**
 * 监听 {@link CommentEvent}（一级评论）/ {@link ReplyEvent}（二级回复）：
 * 在评论事务提交后，生成通知落库（幂等），并实时推送给接收方。
 *
 * <p>对齐文档 docs/modules/10-comment-module.md §8 通知规则：</p>
 * <ul>
 *   <li>一级评论 → 通知<b>帖子作者</b>（type=2 评论，targetType=1 帖子，targetId=postId）；</li>
 *   <li>二级回复 → 通知<b>被回复者</b> replyToUserId（type=3 回复，targetType=1 帖子，targetId=postId）；</li>
 *   <li>去重：评论者 == 接收者（自己评自己帖 / 自己回自己）不发通知；</li>
 *   <li>幂等键：一级 {@code comment:{postId}:{commentId}:{receiverId}}，
 *       二级 {@code reply:{commentId}:{receiverId}}——同一评论固定一条通知，天然幂等；</li>
 *   <li>{@code commentId} 落 notification.commentId，前端据此锚定楼中楼评论。</li>
 * </ul>
 * 落库 + 推送复用 {@link NotificationService#sendNotification} 公共写入方法。
 */
@Slf4j
@Component
public class CommentEventListener {

    /** 预览文案内容摘要最大长度（超出截断追加省略号） */
    private static final int PREVIEW_MAX_LENGTH = 50;

    @Resource
    private UserService userService;

    private final NotificationService notificationService;

    public CommentEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 一级评论：通知帖子作者（type=2 评论）。
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComment(CommentEvent event) {
        Long postId = event.getPostId();
        Long commentId = event.getCommentId();
        Long commentUserId = event.getCommentUserId();   // 评论者（发送方）
        Long postUserId = event.getPostUserId();         // 帖主（接收方）

        // 参数防御 + 自评去重：自己评论自己的帖子，不通知自己
        if (postId == null || commentId == null || commentUserId == null
                || postUserId == null || commentUserId.equals(postUserId)) {
            return;
        }

        // 评论者可能已被删除/注销，防御性跳过
        User user = userService.getById(commentUserId);
        if (user == null) {
            return;
        }

        String content = user.getNickname() + "评论了你：" + preview(event.getContent());

        // type=2 评论；targetType=1 帖子；targetId=帖子内部 id；commentId=评论锚点
        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(postUserId);
        dto.setSenderId(commentUserId);
        dto.setType(2);
        dto.setTargetType(1);
        dto.setTargetId(postId);
        dto.setCommentId(commentId);
        dto.setContent(content);
        dto.setBizKey("comment:" + postId + ":" + commentId + ":" + postUserId);
        notificationService.sendNotification(dto);
    }

    /**
     * 二级回复：通知被回复者 replyToUserId（type=3 回复）。
     * 回复者回的是帖主的楼中楼时，仅此一条 type=3，不再发 type=2（一级评论事件本就不触发）。
     */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReply(ReplyEvent event) {
        Long postId = event.getPostId();
        Long commentId = event.getCommentId();
        Long commentUserId = event.getCommentUserId();   // 回复者（发送方）
        Long replyToUserId = event.getReplyToUserId();   // 被回复者（接收方）

        // 参数防御 + 自回去重：自己回复自己，不通知自己
        if (postId == null || commentId == null || commentUserId == null
                || replyToUserId == null || commentUserId.equals(replyToUserId)) {
            return;
        }

        // 回复者可能已被删除/注销，防御性跳过
        User user = userService.getById(commentUserId);
        if (user == null) {
            return;
        }

        String content = user.getNickname() + "回复了你：" + preview(event.getContent());

        // type=3 回复；targetType=1 帖子；targetId=帖子内部 id；commentId=评论锚点
        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(replyToUserId);
        dto.setSenderId(commentUserId);
        dto.setType(3);
        dto.setTargetType(1);
        dto.setTargetId(postId);
        dto.setCommentId(commentId);
        dto.setContent(content);
        dto.setBizKey("reply:" + commentId + ":" + replyToUserId);
        notificationService.sendNotification(dto);
    }

    /**
     * 内容摘要截断：空白返回空串，超长截断并追加省略号。
     */
    private String preview(String text) {
        if (StrUtil.isBlank(text)) {
            return "";
        }
        String plain = StrUtil.trim(text);
        return plain.length() > PREVIEW_MAX_LENGTH
                ? StrUtil.sub(plain, 0, PREVIEW_MAX_LENGTH) + "…"
                : plain;
    }
}
