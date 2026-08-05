package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.component.SensitiveWordFilter;
import com.ruwei.domain.dto.PostDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.PostImage;
import com.ruwei.domain.empty.PostTag;
import com.ruwei.domain.empty.Tag;
import com.ruwei.mapper.PostMapper;
import com.ruwei.service.BoardService;
import com.ruwei.service.PostImageService;
import com.ruwei.service.PostService;
import com.ruwei.service.PostTagService;
import com.ruwei.service.TagService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
* @author Administrator
* @description 针对表【post(帖子/笔记表(推荐系统物料主表))】的数据库操作Service实现
* @createDate 2026-08-05 10:16:16
*
* <p>实现约定（与 PostService 接口注释一致）：</p>
* <ul>
*   <li>作者一律取当前登录态内部 id（= Sa-Token loginId），不信任前端传参；</li>
*   <li>创建送审：status=3 + auditStatus=1；编辑先发后审：新内容暂存 pending* 字段；</li>
*   <li>图片/标签全量替换：创建/审核应用时删旧插新写 post_image、tag/post_tag；</li>
*   <li>板块帖子数口径：创建时 +1（审核中/驳回也算帖子），删除时 -1；</li>
*   <li>对外编码 postCode 基于 Redis 原子自增（key {@code isegoria:post:code:counter}，前缀 P）。</li>
* </ul>
*/
@Service
public class PostServiceImpl extends ServiceImpl<PostMapper, Post>
    implements PostService{

    /** postCode 计数器 Redis key（对外编码，对齐 userId 的生成方式） */
    private static final String POST_CODE_COUNTER_KEY = "isegoria:post:code:counter";

    /** postCode 自增起始基准 */
    private static final long POST_CODE_BASE = 100000L;

    /** 允许作者直接设置的状态：1已发布 2草稿 4下架（3审核中由审核流控制） */
    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_DRAFT = 2;
    private static final int STATUS_REVIEWING = 3;
    private static final int STATUS_OFFLINE = 4;

    @Resource
    private PostImageService postImageService;

    @Resource
    private TagService tagService;

    @Resource
    private PostTagService postTagService;

    @Resource
    private BoardService boardService;

    @Resource
    private UserService userService;

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 创建帖子（送审）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Post createPost(PostDTO dto) {
        // 当前登录用户内部 id（= Sa-Token loginId）
        long loginId = StpUtil.getLoginIdAsLong();

        // 1. 参数校验
        ThrowUtils.throwIf(BeanUtil.isEmpty(dto), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getTitle()), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getContent()), ErrorCode.PARAMS_ERROR, "内容不能为空");
        ThrowUtils.throwIf(dto.getTitle().length() > 200, ErrorCode.PARAMS_ERROR, "标题最多200字");

        // 2. 板块存在性校验（boardId 非空时）
        if (dto.getBoardId() != null) {
            Board board = boardService.getById(dto.getBoardId());
            ThrowUtils.throwIf(BeanUtil.isEmpty(board), ErrorCode.NOT_FOUND_ERROR, "板块不存在");
        }

        // 3. 敏感词过滤（拦截即拒；替换词脱敏后存储）
        String title = scrub(dto.getTitle(), "标题");
        String content = scrub(dto.getContent(), "正文");
        String topic = StrUtil.isBlank(dto.getTopic()) ? null : scrub(dto.getTopic(), "话题");

        // 4. 组装并落库（创建送审：status=3 审核中 + auditStatus=1 待审）
        Post post = new Post();
        post.setPostCode(generatePostCode());
        post.setUserId(loginId);
        post.setBoardId(dto.getBoardId());
        post.setTitle(title);
        post.setContent(content);
        post.setCover(dto.getCover());
        post.setType(dto.getType() == null ? 1 : dto.getType());
        post.setVideoUrl(dto.getVideoUrl());
        post.setTopic(topic);
        post.setVisibility(dto.getVisibility() == null ? 1 : dto.getVisibility());
        post.setStatus(STATUS_REVIEWING);
        post.setAuditStatus(1);   // 待审
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setCollectCount(0);
        post.setViewCount(0);
        post.setShareCount(0);
        post.setIsTop(0);
        post.setIsEssence(0);
        post.setLatitude(dto.getLatitude());
        post.setLongitude(dto.getLongitude());
        post.setLocationName(dto.getLocationName());
        boolean saved = save(post);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "发布失败");

        // 5. 图片全量写入（post_image）
        saveImages(post.getId(), dto.getImages());

        // 6. 标签写入（tag upsert + post_tag 关联）
        bindTags(post.getId(), dto.getTags());

        // 7. 板块帖子数 +1
        if (dto.getBoardId() != null) {
            incrementBoardPostCount(dto.getBoardId(), 1);
        }

        return post;
    }

    /**
     * 编辑帖子（先发后审；草稿直改）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePost(PostDTO dto) {
        long loginId = StpUtil.getLoginIdAsLong();

        ThrowUtils.throwIf(BeanUtil.isEmpty(dto) || dto.getId() == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        Post post = getById(dto.getId());
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(!Objects.equals(post.getUserId(), loginId), ErrorCode.NO_AUTH_ERROR, "只能编辑自己的帖子");

        // 敏感词过滤（编辑的内容同样拦截/脱敏）
        String title = StrUtil.isBlank(dto.getTitle()) ? null : scrub(dto.getTitle(), "标题");
        String content = StrUtil.isBlank(dto.getContent()) ? null : scrub(dto.getContent(), "正文");
        String topic = StrUtil.isBlank(dto.getTopic()) ? null : scrub(dto.getTopic(), "话题");

        if (post.getStatus() == STATUS_DRAFT) {
            // ===== 草稿：直接改正式字段，不走审核 =====
            Post update = new Post();
            update.setId(post.getId());
            if (title != null) { update.setTitle(title); }
            if (content != null) { update.setContent(content); }
            if (dto.getCover() != null) { update.setCover(dto.getCover()); }
            if (topic != null) { update.setTopic(topic); }
            if (dto.getVideoUrl() != null) { update.setVideoUrl(dto.getVideoUrl()); }
            if (dto.getLocationName() != null) { update.setLocationName(dto.getLocationName()); }
            if (dto.getVisibility() != null) { update.setVisibility(dto.getVisibility()); }
            boolean updated = updateById(update);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "编辑失败");
            // 图片/标签全量替换
            if (dto.getImages() != null) { rebuildImages(post.getId(), dto.getImages()); }
            if (dto.getTags() != null) { rebuildTags(post.getId(), dto.getTags()); }
            return;
        }

        // ===== 已发布/审核中/下架：先发后审，新内容暂存 pending 字段 =====
        // 审核中（status=3）再次编辑则覆盖 pending，重新送审
        LambdaUpdateWrapper<Post> uw = new LambdaUpdateWrapper<>();
        uw.eq(Post::getId, post.getId())
          .set(title != null, Post::getPendingTitle, title)
          .set(content != null, Post::getPendingContent, content)
          .set(dto.getCover() != null, Post::getPendingCover, dto.getCover())
          .set(dto.getImages() != null, Post::getPendingImages, JSONUtil.toJsonStr(dto.getImages()))
          .set(dto.getTags() != null, Post::getPendingTags, JSONUtil.toJsonStr(dto.getTags()))
          .set(Post::getStatus, STATUS_REVIEWING)
          .set(Post::getAuditStatus, 1);
        boolean updated = update(uw);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "编辑失败");
    }

    /**
     * 设置帖子状态（不走审核）。
     */
    @Override
    public void updatePostStatus(Long id, Integer status) {
        long loginId = StpUtil.getLoginIdAsLong();

        ThrowUtils.throwIf(id == null || status == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        Post post = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(!Objects.equals(post.getUserId(), loginId), ErrorCode.NO_AUTH_ERROR, "只能操作自己的帖子");
        // 允许作者直接设置：1已发布 2草稿 4下架（审核中必须等审核结果，不允许手动绕过）
        ThrowUtils.throwIf(!(status == STATUS_PUBLISHED || status == STATUS_DRAFT || status == STATUS_OFFLINE),
                ErrorCode.PARAMS_ERROR, "非法的帖子状态");
        ThrowUtils.throwIf(post.getStatus() == STATUS_REVIEWING, ErrorCode.OPERATION_ERROR, "帖子审核中，请等待审核结果");

        LambdaUpdateWrapper<Post> uw = new LambdaUpdateWrapper<>();
        uw.eq(Post::getId, id).set(Post::getStatus, status);
        // 草稿 → 发布：内容从未公开过，直接视为审核通过
        if (status == STATUS_PUBLISHED && post.getStatus() == STATUS_DRAFT) {
            uw.set(Post::getAuditStatus, 2);
        }
        boolean updated = update(uw);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "设置状态失败");
    }

    /**
     * 删除帖子（逻辑删除 + 关联清理）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deletePost(Long id) {
        long loginId = StpUtil.getLoginIdAsLong();

        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "帖子 id 不能为空");
        Post post = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(!Objects.equals(post.getUserId(), loginId) && !userService.isAdmin(),
                ErrorCode.NO_AUTH_ERROR, "无权限删除该帖子");

        // 1. 逻辑删除（@TableLogic → isDelete=1）
        boolean removed = removeById(id);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "删除失败");

        // 2. 清理关联：post_image 物理删、post_tag 物理删 + 标签 useCount 回退
        postImageService.remove(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, id));
        List<PostTag> postTags = postTagService.lambdaQuery().eq(PostTag::getPostId, id).list();
        for (PostTag pt : postTags) {
            tagService.lambdaUpdate().eq(Tag::getId, pt.getTagId())
                    .setSql("useCount = useCount - 1").update();
        }
        postTagService.remove(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, id));

        // 3. 板块帖子数 -1
        if (post.getBoardId() != null) {
            incrementBoardPostCount(post.getBoardId(), -1);
        }
    }

    /**
     * 管理员审核帖子（通过=应用 pending；驳回=丢弃 pending）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditPost(Long id, Boolean pass) {
        ThrowUtils.throwIf(id == null || pass == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        Post post = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        if (pass) {
            // ===== 审核通过：pending 覆盖正式字段 =====
            LambdaUpdateWrapper<Post> uw = new LambdaUpdateWrapper<>();
            uw.eq(Post::getId, id);
            if (StrUtil.isNotBlank(post.getPendingTitle())) { uw.set(Post::getTitle, post.getPendingTitle()); }
            if (StrUtil.isNotBlank(post.getPendingContent())) { uw.set(Post::getContent, post.getPendingContent()); }
            if (StrUtil.isNotBlank(post.getPendingCover())) { uw.set(Post::getCover, post.getPendingCover()); }
            uw.set(Post::getStatus, STATUS_PUBLISHED)
              .set(Post::getAuditStatus, 2)
              .setSql("pendingTitle = NULL, pendingContent = NULL, pendingCover = NULL, pendingImages = NULL, pendingTags = NULL");
            boolean updated = update(uw);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "审核操作失败");

            // 应用待审图片/标签（全量替换）
            if (StrUtil.isNotBlank(post.getPendingImages())) {
                rebuildImages(id, JSONUtil.toList(post.getPendingImages(), String.class));
            }
            if (StrUtil.isNotBlank(post.getPendingTags())) {
                rebuildTags(id, JSONUtil.toList(post.getPendingTags(), String.class));
            }

            // 创建送审通过（此前 status=3 未计入）：板块帖子数 +1
            if (post.getStatus() == STATUS_REVIEWING && post.getBoardId() != null) {
                incrementBoardPostCount(post.getBoardId(), 1);
            }
        } else {
            // ===== 审核驳回：丢弃 pending =====
            LambdaUpdateWrapper<Post> uw = new LambdaUpdateWrapper<>();
            uw.eq(Post::getId, id)
              .set(Post::getAuditStatus, 3)
              .setSql("pendingTitle = NULL, pendingContent = NULL, pendingCover = NULL, pendingImages = NULL, pendingTags = NULL");
            if (StrUtil.isNotBlank(post.getPendingContent())) {
                // 编辑驳回：旧内容继续对外展示
                uw.set(Post::getStatus, STATUS_PUBLISHED);
            } else {
                // 创建驳回：内容未过审，帖子不可见（下架）
                uw.set(Post::getStatus, STATUS_OFFLINE);
            }
            boolean updated = update(uw);
            ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "审核操作失败");
        }
    }

    // ==================== 私有工具方法 ====================

    /**
     * 敏感词扫描与处置（对齐 BoardServiceImpl.scrubText）：
     * 命中拦截词直接拒绝；命中替换词脱敏为 ***；审核词/放行保留原文。
     */
    private String scrub(String text, String fieldName) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        SensitiveWordFilter.FilterResult fr = sensitiveWordFilter.filter(text);
        ThrowUtils.throwIf(fr.action == SensitiveWordFilter.SensitiveAction.INTERCEPT,
                ErrorCode.PARAMS_ERROR, fieldName + "包含敏感或违规内容，请修改后重试");
        if (fr.action == SensitiveWordFilter.SensitiveAction.REPLACED) {
            return fr.processedText;
        }
        return text;
    }

    /**
     * 生成对外编码 postCode：Redis 原子自增 + "P" 前缀。
     * 计数器不存在时以基准 100000 初始化（setIfAbsent 仅一次），与 userId 生成方式对齐。
     */
    private String generatePostCode() {
        if (!stringRedisTemplate.hasKey(POST_CODE_COUNTER_KEY)) {
            stringRedisTemplate.opsForValue().setIfAbsent(POST_CODE_COUNTER_KEY, String.valueOf(POST_CODE_BASE));
        }
        long n = stringRedisTemplate.opsForValue().increment(POST_CODE_COUNTER_KEY);
        return "P" + n;
    }

    /**
     * 全量写入图片（按列表顺序 sort 递增；空列表/空 URL 跳过）。
     */
    private void saveImages(Long postId, List<String> images) {
        if (images == null || images.isEmpty()) {
            return;
        }
        List<PostImage> list = new ArrayList<>();
        int sort = 0;
        for (String url : images) {
            if (StrUtil.isBlank(url)) {
                continue;
            }
            PostImage pi = new PostImage();
            pi.setPostId(postId);
            pi.setUrl(url);
            pi.setSort(sort++);
            list.add(pi);
        }
        if (!list.isEmpty()) {
            postImageService.saveBatch(list);
        }
    }

    /**
     * 重建图片（删旧插新，全量替换语义）。
     */
    private void rebuildImages(Long postId, List<String> images) {
        postImageService.remove(new LambdaQueryWrapper<PostImage>().eq(PostImage::getPostId, postId));
        saveImages(postId, images);
    }

    /**
     * 绑定标签：tag 不存在则创建（useCount=1），存在则 useCount+1；写 post_tag 关联（ukPostTag 防重）。
     */
    private void bindTags(Long postId, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (String name : tags) {
            if (StrUtil.isBlank(name)) {
                continue;
            }
            Tag tag = tagService.lambdaQuery().eq(Tag::getName, name).one();
            if (tag == null) {
                tag = new Tag();
                tag.setName(name);
                tag.setUseCount(1);
                tag.setStatus(1);
                tagService.save(tag);
            } else {
                tagService.lambdaUpdate().eq(Tag::getId, tag.getId())
                        .setSql("useCount = useCount + 1").update();
            }
            PostTag pt = new PostTag();
            pt.setPostId(postId);
            pt.setTagId(tag.getId());
            try {
                postTagService.save(pt);
            } catch (DuplicateKeyException e) {
                // 已关联过，忽略
            }
        }
    }

    /**
     * 重建标签（删旧关联 + 旧标签 useCount 回退，再按新列表绑定）。
     */
    private void rebuildTags(Long postId, List<String> tags) {
        List<PostTag> old = postTagService.lambdaQuery().eq(PostTag::getPostId, postId).list();
        for (PostTag pt : old) {
            tagService.lambdaUpdate().eq(Tag::getId, pt.getTagId())
                    .setSql("useCount = useCount - 1").update();
        }
        postTagService.remove(new LambdaQueryWrapper<PostTag>().eq(PostTag::getPostId, postId));
        bindTags(postId, tags);
    }

    /**
     * 原子自增/自减板块帖子数（DB 层 SQL 自增减）。
     */
    private void incrementBoardPostCount(Long boardId, int delta) {
        LambdaUpdateWrapper<Board> uw = new LambdaUpdateWrapper<>();
        uw.eq(Board::getId, boardId)
          .setSql("postCount = postCount " + (delta >= 0 ? "+ " : "- ") + Math.abs(delta));
        boardService.update(uw);
    }
}
