package com.ruwei.schedule;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruwei.domain.empty.Post;
import com.ruwei.es.doc.PostDoc;
import com.ruwei.es.listener.PostIndexEventListener;
import com.ruwei.es.service.EsPostSyncService;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

/**
 * ES 索引一致性定时对账（每日凌晨 3:00）：
 *
 * <ol>
 *   <li><b>失败重试</b>：重放 Redis 中 {@code es:sync:fail:ids} 记录的同步失败帖子 id
 *       （由 {@link PostIndexEventListener} 写入），成功即移出队列；</li>
 *   <li><b>全量对账</b>：对比「MySQL 应索引的帖子 id 集合」与「ES 中已存在的 id 集合」，
 *       缺失的补索引（少补），多余的删索引（多删），保证最终一致。</li>
 * </ol>
 */
@Slf4j
@Component
public class EsReconcileTask {

    @Resource
    private EsPostSyncService esPostSyncService;
    @Resource
    private ElasticsearchOperations operations;
    @Resource
    private PostService postService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /** 每批处理量 */
    private static final long BATCH_SIZE = 500L;

    /**
     * 每日凌晨 3:00 执行。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void reconcile() {
        log.info("========== ES 定时对账开始 ==========");
        try {
            retryFailedSync();
            doReconcile();
        } catch (Exception e) {
            log.error("ES 定时对账异常", e);
        }
        log.info("========== ES 定时对账结束 ==========");
    }

    /**
     * 1. 重试 Redis 失败队列：indexByPostId 内部会按当前状态判断
     * （满足 shouldIndex → 索引；不满足 → 删除），语义安全。
     */
    private void retryFailedSync() {
        Set<String> failedIds = stringRedisTemplate.opsForSet()
                .members(PostIndexEventListener.ES_SYNC_FAIL_KEY);
        if (failedIds == null || failedIds.isEmpty()) {
            return;
        }
        for (String idStr : failedIds) {
            try {
                esPostSyncService.indexByPostId(Long.valueOf(idStr));
                stringRedisTemplate.opsForSet().remove(PostIndexEventListener.ES_SYNC_FAIL_KEY, idStr);
                log.info("ES 失败重试成功 postId={}", idStr);
            } catch (Exception e) {
                log.warn("ES 失败重试仍失败 postId={}，保留待下轮重试", idStr);
            }
        }
    }

    /**
     * 2. 全量对账：MySQL 应索引集合 vs ES 现有集合，少补多删。
     */
    private void doReconcile() {
        // 2.1 MySQL 侧：分页扫未删除帖子，过滤 shouldIndex，得到应索引 id 集合
        Set<Long> mysqlIds = new HashSet<>();
        long current = 1;
        while (true) {
            Page<Post> page = postService.lambdaQuery()
                    .orderByAsc(Post::getId)
                    .page(new Page<>(current, BATCH_SIZE));
            page.getRecords().stream()
                    .filter(esPostSyncService::shouldIndex)
                    .map(Post::getId)
                    .forEach(mysqlIds::add);
            if (!page.hasNext()) {
                break;
            }
            current++;
        }

        // 2.2 ES 侧：分页扫出全部文档 id
        // 注意：from+size 深分页上限默认 1w，一期数据量足够；超量需改 search_after
        Set<Long> esIds = new HashSet<>();
        int pageNum = 0;
        while (true) {
            NativeQuery q = NativeQuery.builder()
                    .withQuery(qb -> qb.matchAll(m -> m))
                    .withPageable(PageRequest.of(pageNum, (int) BATCH_SIZE))
                    .build();
            SearchHits<PostDoc> hits = operations.search(q, PostDoc.class);
            for (SearchHit<PostDoc> h : hits) {
                esIds.add(Long.valueOf(h.getId()));
            }
            if (hits.getSearchHits().size() < BATCH_SIZE) {
                break;
            }
            pageNum++;
        }

        // 2.3 补偿：MySQL 有而 ES 没有 → 补索引
        Set<Long> missing = new HashSet<>(mysqlIds);
        missing.removeAll(esIds);
        if (!missing.isEmpty()) {
            for (Long id : missing) {
                esPostSyncService.indexByPostId(id);
            }
            log.info("ES 对账：补索引 {} 条 {}", missing.size(), missing);
        }

        // 2.4 补偿：ES 有而 MySQL 不应有 → 删索引
        Set<Long> extra = new HashSet<>(esIds);
        extra.removeAll(mysqlIds);
        if (!extra.isEmpty()) {
            for (Long id : extra) {
                esPostSyncService.deleteByPostId(id);
            }
            log.info("ES 对账：删索引 {} 条 {}", extra.size(), extra);
        }

        log.info("ES 对账完成：MySQL 应索引 {} 条，ES 现有 {} 条，补 {} 删 {}", 
                mysqlIds.size(), esIds.size(), missing.size(), extra.size());
    }
}
