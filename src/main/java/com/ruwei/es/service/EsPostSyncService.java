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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
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

    /**
     * 按作者重建索引（用户资料变更时调用，对齐 UserProfileUpdatedEvent 消费端）。
     *
     * <p>该作者全部帖子中满足 {@link #shouldIndex} 的批量重建（作者/标签一次查，避免 N 次回表），
     * 不满足条件的 {@code deleteById} 清理脏索引（防已下架/私密帖残留）。</p>
     *
     * @param userId 作者内部 id（null 直接跳过）
     */
    public void reindexByAuthorId(Long userId) {
        if (userId == null) {
            return;
        }
        List<Post> posts = postService.lambdaQuery()
                .eq(Post::getUserId, userId)
                .list();
        if (posts.isEmpty()) {
            return;
        }
        // 作者一次查（该作者所有帖子的 nickname/avatar 相同）
        User author = userService.getById(userId);
        // 全部话题 id 一次批量查（避免 toDoc 逐帖查 tag）
        Set<Long> allTagIds = new HashSet<>();
        for (Post p : posts) {
            if (StrUtil.isBlank(p.getTopic())) {
                continue;
            }
            StrUtil.split(p.getTopic(), ',').stream()
                    .filter(NumberUtil::isLong)
                    .map(Long::valueOf)
                    .forEach(allTagIds::add);
        }

        Map<Long, Tag> tagMap = allTagIds.isEmpty() ? Map.of()
                : tagService.lambdaQuery().in(Tag::getId, allTagIds).list().stream()
                        .collect(Collectors.toMap(Tag::getId, t -> t, (a, b) -> a));

        List<PostDoc> docs = new ArrayList<>();
        for (Post post : posts) {
            if (!shouldIndex(post)) {
                // 不满足索引条件就确保索引里没有它（防止脏数据）
                postEsMapper.deleteById(post.getId());
                continue;
            }
            docs.add(toDoc(post, author, tagMap));
        }
        if (!docs.isEmpty()) {
            postEsMapper.saveAll(docs);
        }
        log.info("用户资料变更触发 ES 重建完成 authorId={} 重建 {} 条", userId, docs.size());
    }

    /**
     * 该作者全部帖子 id（供失败重试队列 {@code es:sync:fail:ids} 写入，由对账任务逐帖重试）。
     *
     * @param userId 作者内部 id
     * @return 该作者全部帖子 id（可能为空列表）
     */
    public List<Long> listPostIdsByAuthor(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return postService.lambdaQuery()
                .eq(Post::getUserId, userId)
                .list().stream()
                .map(Post::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /** Post -> PostDoc（单条，内部回表查作者/标签，供 indexByPostId / fullReindex 使用） */
    private PostDoc toDoc(Post post) {
        return toDoc(post, userService.getById(post.getUserId()), null);
    }

    /**
     * Post -> PostDoc（批量版，作者/标签由调用方一次性查出传入，避免 N+1）。
     *
     * @param post   帖子实体
     * @param author 已查出的作者（可 null，置空昵称/头像）
     * @param tagMap 已查出的标签索引（可 null，回退逐帖查库）
     */
    private PostDoc toDoc(Post post, User author, Map<Long, Tag> tagMap) {
        PostDoc d = new PostDoc();
        d.setId(post.getId());
        d.setPostCode(post.getPostCode());
        d.setUserId(post.getUserId());
        d.setBoardId(post.getBoardId());
        d.setTitle(post.getTitle());
        d.setPlainText(extractPlainText(post.getContent()));
        d.setTagNames(tagNamesOf(post.getTopic(), tagMap));
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

    /** topic（tag id 逗号串）→ 标签名称列表（单条路径，逐帖查库） */
    private List<String> tagNamesOf(String topic) {
        return tagNamesOf(topic, null);
    }

    /**
     * topic（tag id 逗号串）→ 标签名称列表（批量路径）。
     *
     * @param topic  帖子话题串（tag id 逗号分隔）
     * @param tagMap 调用方批量查出的标签索引（null 时回退逐帖查库）
     */
    private List<String> tagNamesOf(String topic, Map<Long, Tag> tagMap) {
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
        if (tagMap != null) {
            // 批量路径：直接从索引取值，不再逐帖查库
            return ids.stream()
                    .map(tagMap::get)
                    .filter(Objects::nonNull)
                    .map(Tag::getName)
                    .collect(Collectors.toList());
        }
        return tagService.lambdaQuery().in(Tag::getId, ids).list().stream()
                .map(Tag::getName)
                .collect(Collectors.toList());
    }
}