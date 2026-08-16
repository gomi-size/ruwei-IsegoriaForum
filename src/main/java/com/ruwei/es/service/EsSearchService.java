package com.ruwei.es.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruwei.domain.vo.PostBrowseVO;
import com.ruwei.es.doc.PostDoc;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;
import jakarta.annotation.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class EsSearchService {

    @Resource
    private ElasticsearchOperations operations;

    /**
     * 帖子搜索。
     *
     * <p>匹配逻辑（keyword 非空时二者取 OR，至少命中其一）：</p>
     * <ol>
     *   <li><b>整词匹配</b>：multiMatch 多字段 title / plainText / tagNames / <b>nickname</b>
     *       （搜用户昵称可命中其公开帖子，如系统默认昵称 {@code ISEGORIA_xxxxxx}）；</li>
     *   <li><b>昵称子串模糊</b>：wildcard 对 nickname 字段做 {@code *keyword*} 任意位置通配
     *       （IK 分词产出小写 token，查询串统一小写 + 转义通配符），
     *       支持输入半截昵称（如 {@code GhTq}）也能命中。</li>
     * </ol>
     *
     * @param keyword 关键词（可为空，空则按条件浏览）
     * @param boardId 板块过滤（可空）
     * @param type    内容形态过滤（可空）
     * @param sort    排序：score(热度) / time(最新) / 默认相关度
     */
    public Page<PostBrowseVO> searchPost(String keyword, Long boardId, Integer type,
                                         String sort, long current, long pageSize) {
        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> {
                    b.filter(f -> f.term(t -> t.field("visibility").value(1))); // 只搜公开
                    if (boardId != null) {
                        b.filter(f -> f.term(t -> t.field("boardId").value(boardId)));
                    }
                    if (type != null) {
                        b.filter(f -> f.term(t -> t.field("type").value(type)));
                    }
                    if (StrUtil.isNotBlank(keyword)) {
                        // 昵称子串通配：小写化（索引 token 为小写）+ 转义 wildcard 特殊字符（\ * ?）
                        String escaped = keyword.toLowerCase(Locale.ROOT)
                                .replace("\\", "\\\\")
                                .replace("*", "\\*")
                                .replace("?", "\\?");
                        b.must(m -> m.bool(mb -> mb
                                .should(sh -> sh.multiMatch(mm -> mm
                                        .fields("title", "plainText", "tagNames", "nickname")
                                        .query(keyword)))
                                .should(sh -> sh.wildcard(w -> w.field("nickname").value("*" + escaped + "*")))
                                .minimumShouldMatch("1")));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of((int) (current - 1), (int) pageSize, buildSort(sort)))
                .withHighlightQuery(buildHighlight())
                .build();

        /*long start = System.currentTimeMillis();*/
        SearchHits<PostDoc> hits = operations.search(query, PostDoc.class);
/*
        log.info("ES 搜索 keyword={} boardId={} type={} sort={} 命中 {} 条，耗时 {}ms",
                keyword, boardId, type, sort, hits.getTotalHits(), System.currentTimeMillis() - start);
*/

        List<PostBrowseVO> list = new ArrayList<>();
        for (SearchHit<PostDoc> hit : hits) {
            PostBrowseVO vo = toBrowseVO(hit.getContent());
            // 高亮片段覆盖 title（含 <em>）
            List<String> titleHL = hit.getHighlightField("title");
            if (titleHL != null && !titleHL.isEmpty()) {
                vo.setTitle(titleHL.get(0));
            }
            // 高亮片段覆盖预览正文（命中上下文片段，天然即摘要，且保留 <em> 命中标记）
            List<String> plainHL = hit.getHighlightField("plainText");
            if (plainHL != null && !plainHL.isEmpty()) {
                vo.setContentPreview(plainHL.get(0));
            }
            list.add(vo);
        }

        Page<PostBrowseVO> page = new Page<>(current, pageSize);
        page.setRecords(list);
        page.setTotal(hits.getTotalHits());
        return page;
    }

    private Sort buildSort(String sort) {
        if ("score".equals(sort)) {
            return Sort.by(Sort.Order.desc("score"), Sort.Order.desc("createdAt"));
        }
        if ("time".equals(sort)) {
            return Sort.by(Sort.Order.desc("createdAt"));
        }
        // 默认相关度（_score），ES 默认行为，不传 Sort
        return Sort.unsorted();
    }

    private HighlightQuery buildHighlight() {
        Highlight highlight = new Highlight(HighlightParameters.builder()
                .withPreTags(new String[]{"<em>"})
                .withPostTags(new String[]{"</em>"})
                .build(),
                List.of(new HighlightField("title"),
                        new HighlightField("plainText")));
        return new HighlightQuery(highlight, PostDoc.class);
    }

    private PostBrowseVO toBrowseVO(PostDoc d) {
        PostBrowseVO vo = new PostBrowseVO();
        vo.setId(d.getId());
        vo.setPostCode(d.getPostCode());
        vo.setUserId(d.getUserId());
        vo.setUserNickname(d.getNickname());
        vo.setUserAvatar(d.getAvatar());
        vo.setTitle(d.getTitle());
        vo.setCover(d.getCover());
        // 默认预览正文：ES 纯文本字段截断（命中关键词时上方用高亮片段覆盖）
        vo.setContentPreview(StrUtil.maxLength(d.getPlainText(), 100));
        vo.setType(d.getType());
        vo.setLikeCount(d.getLikeCount());
        vo.setCommentCount(d.getCommentCount());
        vo.setCollectCount(d.getCollectCount());
        vo.setViewCount(d.getViewCount());
        vo.setIsTop(d.getIsTop());
        vo.setIsEssence(d.getIsEssence());
        vo.setCreatedAt(d.getCreatedAt());
        return vo;
    }
}
