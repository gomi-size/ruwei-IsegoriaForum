package com.ruwei.es.service;

import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.ContentBlock;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.Tag;
import com.ruwei.domain.empty.User;
import com.ruwei.es.doc.PostDoc;
import com.ruwei.es.mapper.PostEsMapper;
import com.ruwei.service.PostService;
import com.ruwei.service.TagService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class EsPostSyncService {

    @Resource
    private PostEsMapper postEsMapper;
    @Resource
    @Lazy
    private PostService postService;
    @Resource
    private UserService userService;
    @Resource
    private TagService tagService;

    /**
     * 是否应进入 ES 索引（安全底线，单点判断）：
     * 仅「已发布 + 审核通过 + 公开 + 未删除」的帖子。
     */
    public boolean shouldIndex(Post post) {
        return post != null
                && post.getIsDelete() != null && post.getIsDelete() == 0
                && PostStatusEnum.PUBLISHED.matches(post.getStatus())
                && PostAuditStatusEnum.APPROVED.matches(post.getAuditStatus())
                && PostVisibilityEnum.PUBLIC.matches(post.getVisibility());
    }

    /** 按 id 增量索引（新增/更新） */
    public void indexByPostId(Long postId) {
        Post post = postService.getById(postId);
        if (post == null) {
            return;
        }
        if (!shouldIndex(post)) {
            // 不满足索引条件就确保索引里没有它（防止脏数据）
            postEsMapper.deleteById(postId);
            return;
        }
        postEsMapper.save(toDoc(post));
    }

    /** 按 id 删除索引 */
    public void deleteByPostId(Long postId) {
        postEsMapper.deleteById(postId);
    }

    /** 全量重建：分页扫 post 表，只索引满足 shouldIndex 的帖子 */
    public void fullReindex() {
        long current = 1;
        long size = 500;
        long total = 0;
        while (true) {
            Page<Post> page = postService.lambdaQuery()
                    .orderByAsc(Post::getId)
                    .page(new Page<>(current, size));
            List<PostDoc> docs = page.getRecords().stream()
                    .filter(this::shouldIndex)
                    .map(this::toDoc)
                    .collect(Collectors.toList());
            if (!docs.isEmpty()) {
                postEsMapper.saveAll(docs);
                total += docs.size();
            }
            if (!page.hasNext()) {
                break;
            }
            current++;
        }
        log.info("ES 全量重建完成，共索引 {} 条帖子", total);
    }

    /** Post -> PostDoc */
    private PostDoc toDoc(Post post) {
        PostDoc d = new PostDoc();
        d.setId(post.getId());
        d.setPostCode(post.getPostCode());
        d.setUserId(post.getUserId());
        d.setBoardId(post.getBoardId());
        d.setTitle(post.getTitle());
        d.setPlainText(extractPlainText(post.getContent()));
        d.setTagNames(tagNamesOf(post.getTopic()));
        d.setCover(post.getCover());
        d.setType(post.getType());
        d.setVisibility(post.getVisibility());
        d.setStatus(post.getStatus());
        d.setAuditStatus(post.getAuditStatus());
        d.setLikeCount(post.getLikeCount());
        d.setCommentCount(post.getCommentCount());
        d.setCollectCount(post.getCollectCount());
        d.setViewCount(post.getViewCount());
        d.setShareCount(post.getShareCount());
        d.setScore(post.getScore() == null ? 0d : post.getScore().doubleValue());
        d.setIsTop(post.getIsTop());
        d.setIsEssence(post.getIsEssence());
        d.setCreatedAt(post.getCreatedAt());
        // 冗余作者昵称/头像，避免搜索结果回表 N+1
        User author = userService.getById(post.getUserId());
        if (author != null) {
            d.setNickname(author.getNickname());
            d.setAvatar(author.getAvatar());
        }
        return d;
    }

    /** 正文 content（ContentBlock JSON 数组或纯文本）→ 可检索纯文本 */
    private String extractPlainText(String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<ContentBlock> blocks = JSONUtil.toList(trimmed, ContentBlock.class);
                return blocks.stream()
                        .filter(b -> StrUtil.isNotBlank(b.getText()))
                        .map(ContentBlock::getText)
                        .collect(Collectors.joining("\n"));
            } catch (Exception e) {
                log.warn("正文 blocks 解析失败，退回纯文本处理: {}", e.getMessage());
                return content;
            }
        }
        return content;
    }

    /** topic（tag id 逗号串）→ 标签名称列表 */
    private List<String> tagNamesOf(String topic) {
        if (StrUtil.isBlank(topic)) {
            return List.of();
        }
        List<Long> ids = StrUtil.split(topic, ',').stream()
                .map(StrUtil::trim)
                .filter(NumberUtil::isLong)
                .map(Long::valueOf)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return tagService.lambdaQuery().in(Tag::getId, ids).list().stream()
                .map(Tag::getName)
                .collect(Collectors.toList());
    }
}