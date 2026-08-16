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

@Slf4j
@Service
public class EsSearchService {

    @Resource
    private ElasticsearchOperations operations;

    /**
     * 帖子搜索。
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
                        b.must(m -> m.multiMatch(mm -> mm
                                .fields("title", "plainText", "tagNames")
                                .query(keyword)));
                    }
                    return b;
                }))
                .withPageable(PageRequest.of((int) (current - 1), (int) pageSize, buildSort(sort)))
                .withHighlightQuery(buildHighlight())
                .build();

        SearchHits<PostDoc> hits = operations.search(query, PostDoc.class);

        List<PostBrowseVO> list = new ArrayList<>();
        for (SearchHit<PostDoc> hit : hits) {
            PostBrowseVO vo = toBrowseVO(hit.getContent());
            // 高亮片段覆盖 title（含 <em>）
            List<String> titleHL = hit.getHighlightField("title");
            if (titleHL != null && !titleHL.isEmpty()) {
                vo.setTitle(titleHL.get(0));
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
