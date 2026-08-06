package com.ruwei.component.notification.listener;

import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.component.notification.event.BoardFollowEvent;
import com.ruwei.domain.dto.SendNotificationDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.domain.empty.User;
import com.ruwei.service.BoardService;
import com.ruwei.service.NotificationService;
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

import static java.time.format.DateTimeFormatter.ofPattern;

/**
 * 监听 {@link BoardFollowEvent}：在板块关注事务提交后，生成通知落库（幂等），并实时推送给板块创建者（吧主）。
 *
 * <p>与 {@link FollowUserEventListener}（用户关注用户）的差异：
 * <ul>
 *   <li>接收者是板块创建者 {@code ownerId}（非关注者本人）；</li>
 *   <li>{@code type=4}（关注，与用户关注复用同一类型语义），{@code targetType=3}（板块）区分；</li>
 *   <li>吧主关注自己的板块不通知（自通知跳过）；</li>
 *   <li>幂等键 {@code boardFollow:{actor}:{boardId}:{yyyyMMdd}}，同一关注每天只通知一次。</li>
 * </ul>
 * 落库 + 推送复用 {@link NotificationService#sendNotification} 公共写入方法。</p>
 */
@Slf4j
@Component
public class BoardFollowEventListener {

    @Resource
    private UserService userService;

    @Resource
    private BoardService boardService;

    private final NotificationService notificationService;

    public BoardFollowEventListener(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBoardFollow(BoardFollowEvent event) {
        Long actorId = event.getActorId();      // 主动关注者（内部 id）
        Long boardId = event.getBoardId();      // 被关注板块（内部 id）
        Long ownerId = event.getOwnerId();      // 板块创建者（内部 id）

        // 吧主关注自己的板块 → 不通知自己
        if (actorId == null || ownerId == null || actorId.equals(ownerId)) {
            return;
        }

        // 幂等：同一 (actor, board) 每天只产生一条通知
        String todayStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String bizKey = "boardFollow:" + actorId + ":" + boardId + ":" + todayStr;
        String time = LocalDateTime.now().format(ofPattern("yyyy-MM-dd HH:mm:ss"));

        // 关注者或板块可能已被删除/下架，防御性跳过
        User user = userService.getById(actorId);
        Board board = boardService.getById(boardId);
        if (user == null || board == null) {
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR,"关注未成功，板块者可能已经被删除");
        }

        String content = user.getNickname() + "在"+time+"关注了你的板块「" + board.getName() + "」";

        // type=4 关注；targetType=3 板块；targetId=板块内部 id（前端跳板块主页）
        SendNotificationDTO dto = new SendNotificationDTO();
        dto.setReceiverId(ownerId);
        dto.setSenderId(actorId);
        dto.setType(4);
        dto.setTargetType(3);
        dto.setTargetId(boardId);
        dto.setContent(content);
        dto.setBizKey(bizKey);
        notificationService.sendNotification(dto);
    }
}
