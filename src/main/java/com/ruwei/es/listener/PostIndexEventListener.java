package com.ruwei.es.listener;

import com.ruwei.es.event.PostIndexEvent;

import com.ruwei.es.service.EsPostSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class PostIndexEventListener {

    /** Redis 失败重试队列 key：Set 存同步失败的帖子 id，由定时对账任务重试 */
    public static final String ES_SYNC_FAIL_KEY = "es:sync:fail:ids";

    @Resource
    private EsPostSyncService esPostSyncService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onPostIndex(PostIndexEvent event) {
        try {
            if (event.getAction() == PostIndexEvent.Action.INDEX) {
                esPostSyncService.indexByPostId(event.getPostId());
            } else {
                esPostSyncService.deleteByPostId(event.getPostId());
            }
        } catch (Exception e) {
            // 同步失败不影响主流程：失败 id 记入 Redis，由定时对账任务重试兜底，保证最终一致
            log.error("ES 同步失败 postId={} action={}", event.getPostId(), event.getAction(), e);
            if (event.getPostId() != null) {
                stringRedisTemplate.opsForSet().add(ES_SYNC_FAIL_KEY, String.valueOf(event.getPostId()));
            }
        }
    }
}