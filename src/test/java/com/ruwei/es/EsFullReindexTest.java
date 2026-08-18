package com.ruwei.es;

import com.ruwei.es.doc.PostDoc;
import com.ruwei.es.service.EsPostSyncService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

/**
 * 手动触发 ES 全量同步测试：
 * 调用 {@link EsPostSyncService#fullReindex()} 将 MySQL 中
 * 「已发布 + 审核通过 + 公开 + 未删除」的帖子全量索引到 post_index，
 * 并在同步后打印 ES 中的文档总数。
 *
 * 前置依赖：MySQL(frorum)、Redis、Elasticsearch(localhost:9200) 均在运行。
 */
@Slf4j
@SpringBootTest
@MapperScan("com.ruwei.manager")
class EsFullReindexTest {

    @Resource
    private EsPostSyncService esPostSyncService;

    @Resource
    private ElasticsearchOperations operations;

    @Test

    void fullReindexOnce() {
        log.info("========== 手动触发 ES 全量同步开始 ==========");
        esPostSyncService.fullReindex();
        long count = operations.count(NativeQuery.builder()
                .withQuery(qb -> qb.matchAll(m -> m))
                .build(), PostDoc.class);
        log.info("========== ES 全量同步完成,post_index 文档数: {} ==========", count);
    }
}
