package com.ruwei.es.listener;

import com.ruwei.es.event.UserProfileUpdatedEvent;
import com.ruwei.es.service.EsPostSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听 {@link UserProfileUpdatedEvent}（用户资料变更）：事务提交后异步重建该作者全部 ES 帖子。
 *
 * <p>背景：{@code PostDoc} 冗余作者昵称/头像快照，用户编辑资料后必须重建索引，否则
 * 推荐流 / 搜索仍展示旧昵称头像（对齐 {@code EsPostSyncService#reindexByAuthorId}）。</p>
 *
 * <p>模式与 {@link PostIndexEventListener} 一致：{@code @Async("eventTaskExecutor")} 异步执行、
 * {@code fallbackExecution=true}（编辑无事务时也立即执行）；同步失败不影响主流程，
 * 将该作者全部帖子 id 记入 {@code es:sync:fail:ids}，由 {@code EsReconcileTask} 定时对账重试兜底。</p>
 */
@Slf4j
@Component
public class UserProfileEventListener {

    /** Redis 失败重试队列 key（与 PostIndexEventListener 共用） */
    public static final String ES_SYNC_FAIL_KEY = PostIndexEventListener.ES_SYNC_FAIL_KEY;

    @Resource
    private EsPostSyncService esPostSyncService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserProfileUpdated(UserProfileUpdatedEvent event) {
        Long userId = event.getUserId();
        if (userId == null) {
            return;
        }
        try {
            esPostSyncService.reindexByAuthorId(userId);
        } catch (Exception e) {
            // 重建失败不影响用户编辑主流程：作者全部帖子 id 记入失败队列，由对账任务重试
            log.error("用户资料变更触发 ES 重建失败 userId={}", userId, e);
            try {
                esPostSyncService.listPostIdsByAuthor(userId)
                        .forEach(pid -> stringRedisTemplate.opsForSet().add(ES_SYNC_FAIL_KEY, String.valueOf(pid)));
            } catch (Exception ex) {
                log.warn("写入 ES 失败重试队列异常 userId={}", userId, ex);
            }
        }
    }
}
