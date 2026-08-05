package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.component.SensitiveWordFilter;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.PostDTO;
import com.ruwei.domain.empty.*;
import com.ruwei.domain.utils.CountUtils;
import com.ruwei.domain.vo.PostVO;
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
import java.util.stream.Collectors;

/**
* @author Administrator
* @description 针对表【post(帖子/笔记表(推荐系统物料主表))】的数据库操作Service实现
* @createDate 2026-08-05 10:16:16
*
* <p>实现约定（与 PostService 接口注释一致）：</p>
* <ul>
*   <li>作者一律取当前登录态内部 id（= Sa-Token loginId），不信任前端传参；</li>
*   <li>创建送审：status=3 + auditStatus=1；编辑「先审后发」：直接改正式字段并重新置为审核中；</li>
*   <li>图片/标签<b>按版本状态分组</b>：post_image.status / post_tag.status 记录该行属于帖子的哪个版本
*       （已发布 / 草稿 / 审核中）。写入时只在同一 status 内全量替换（先查、无则插、有则删旧插新），
*       其它版本不受影响；审核结束由 migrateReviewingRelations 把「审核中」版整体顶替为最终状态；</li>
*   <li>板块帖子数口径：仅创建时 +1（审核中/驳回也算帖子），删除时 -1，审核环节不再累加；</li>
*   <li>审核只推进状态、不搬运内容：待审内容在创建/编辑时已落在正式字段上，
*       通过→已发布、驳回→下架（正式字段已被覆盖，无旧版本可回退）；</li>
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
     * 创建帖子（送审）。返回对外展示的 {@link PostVO}（枚举字段回显文字、雪花 id 转字符串）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public PostVO createPost(PostDTO dto) {
        // 当前登录用户内部 id（= Sa-Token loginId）
        long loginId = StpUtil.getLoginIdAsLong();


        // 1. 参数校验
        ThrowUtils.throwIf(BeanUtil.isEmpty(dto), ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getTitle()), ErrorCode.PARAMS_ERROR, "标题不能为空");
        ThrowUtils.throwIf(StrUtil.isBlank(dto.getContent()), ErrorCode.PARAMS_ERROR, "内容不能为空");
        ThrowUtils.throwIf(dto.getTitle().length() > 200, ErrorCode.PARAMS_ERROR, "标题最多200字");
        ThrowUtils.throwIf(dto.getContent().length() > 1500, ErrorCode.PARAMS_ERROR, "内容最多1500字");

        // 2. 板块存在性校验（boardId 非空时）
        if (dto.getBoardId() != null) {
            Board board = boardService.getById(dto.getBoardId());
            ThrowUtils.throwIf(BeanUtil.isEmpty(board), ErrorCode.NOT_FOUND_ERROR, "板块不存在");
        }

        // 3. 敏感词过滤（拦截即拒；替换词脱敏后存储）
        String title = scrub(dto.getTitle(), "标题");
        String content = scrub(dto.getContent(), "正文");

        // 3.5 话题/标签：前端传 tag id 列表 → 查 tag 表（仅 status=1 未被禁用）→ 名称逗号分隔存 post.topic
        List<Tag> tags = resolveTags(dto.getTopicList());
        String topic = tags.isEmpty() ? null : joinTagNames(tags);

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
        // 可见性：前端传文字（"公开"/"仅粉丝可见"/"私密"），经枚举转整数；缺省按"公开"(1)
        Integer visibilityCode = PostVisibilityEnum.codeOfText(dto.getVisibility());
        ThrowUtils.throwIf(StrUtil.isNotBlank(dto.getVisibility()) && visibilityCode == null,
                ErrorCode.PARAMS_ERROR, "非法的可见性：" + dto.getVisibility());
        post.setVisibility(visibilityCode == null ? PostVisibilityEnum.PUBLIC.getCode() : visibilityCode);
        // 创建送审：status=审核中(3) + auditStatus=待审(1)（与枚举常量对齐）
        post.setStatus(PostStatusEnum.REVIEWING.getCode());
        post.setAuditStatus(PostAuditStatusEnum.PENDING.getCode());
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

        // 5. 图片全量写入（post_image，来自 dto.imageUrl）——与帖子同为「审核中」版本
        saveImages(post.getId(), dto.getImageUrl(), PostStatusEnum.REVIEWING.getCode());

        // 6. 标签关联（useCount+1 + post_tag）——同上，标记为「审核中」版本
        bindTags(post.getId(), tags, PostStatusEnum.REVIEWING.getCode());

        // 7. 板块帖子数 +1
        if (dto.getBoardId() != null) {
            CountUtils.increment(boardService, Board::getId, dto.getBoardId(), "postCount", 1);
        }

        //8.作者的作品数加一
        CountUtils.increment(userService, User::getId, loginId, "postCount", 1);

        // 9. 装配对外 VO：枚举字段回显文字、topic 解析为 id 列表、图片按 sort 回读
        return buildPostVO(post);
    }

    /**
     * 编辑帖子（先发后审；草稿直改）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public BaseResponse<String> updatePost(PostDTO dto) {
        long loginId = StpUtil.getLoginIdAsLong();

        ThrowUtils.throwIf(BeanUtil.isEmpty(dto) || dto.getId() == null, ErrorCode.PARAMS_ERROR, "参数不能为空");

        //三个唯一标识联合查询，出现不存在就报错
        Post post = lambdaQuery().eq(Post::getId,dto.getId())
                        .eq(Post::getPostCode,dto.getPostCode())
                        .eq(Post::getUserId,loginId)
                        .one();

        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(!Objects.equals(post.getUserId(), loginId), ErrorCode.NO_AUTH_ERROR, "只能编辑自己的帖子");

        // 敏感词过滤（编辑的内容同样拦截/脱敏）
        String title = StrUtil.isBlank(dto.getTitle()) ? null : scrub(dto.getTitle(), "标题");
        String content = StrUtil.isBlank(dto.getContent()) ? null : scrub(dto.getContent(), "正文");

        // 内容字数限制（最多1500字）
        if (StrUtil.isNotBlank(dto.getContent())) {
            ThrowUtils.throwIf(dto.getContent().length() > 1500, ErrorCode.PARAMS_ERROR, "内容最多1500字");
        }

        // 维护话题：tag id 列表 → 查 tag 表（仅未禁用）→ 名称字符串（null 表示本次未传 topic，不更新）
        List<Tag> topicTags = dto.getTopicList() == null ? null : resolveTags(dto.getTopicList());
        String topic = topicTags == null ? null : joinTagNames(topicTags);

        // ===== 草稿：单独保存一条记录，不走审核（展示用户的一定是已发布的） =====
        if (PostStatusEnum.DRAFT.getText().equals(dto.getStatus())) {
            Post update = BeanUtil.copyProperties(dto, Post.class);
            //单独保存为一个为草稿
            update.setId(null);
            update.setPostCode(dto.getPostCode());
            update.setUserId(loginId);
            update.setIsTop(0);
            update.setTopic(topic != null ? topic : post.getTopic());
            // 可见性：前端传文字 → 枚举转整数；仅在本次传入时覆盖，否则沿用原帖
            if (StrUtil.isNotBlank(dto.getVisibility())) {
                Integer vc = PostVisibilityEnum.codeOfText(dto.getVisibility());
                ThrowUtils.throwIf(vc == null, ErrorCode.PARAMS_ERROR, "非法的可见性：" + dto.getVisibility());
                update.setVisibility(vc);
            } else {
                update.setVisibility(post.getVisibility());
            }
            // 生命周期与审核结果由后端固定：草稿 + 待审。
            update.setStatus(PostStatusEnum.DRAFT.getCode());
            //设置为待审核
            update.setAuditStatus(PostAuditStatusEnum.PENDING.getCode());
            //这里保存草稿
            boolean result = save(update);
            ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "编辑失败");

            // 图片/标签按「新草稿自身的 id + 自身状态」写入；
            // 注意必须用 update.getId()（新草稿），用 post.getId() 会把原帖的图片/标签删掉
            if (dto.getImageUrl() != null) {
                rebuildImages(update.getId(), dto.getImageUrl(), update.getStatus());
            }
            if (topicTags != null) {
                rebuildTags(update.getId(), topicTags, update.getStatus());
            }
            return ResultUtils.success("修改成功，已存入草稿箱");
        }

        // ===== 非草稿：直接改正式字段，并整体重新送审（审核中 + 待审）=====
        // 语义：编辑即下架送审——审核期间 status=审核中，前台不展示；审核通过才恢复已发布。
        // 可见性：前端传文字 → 枚举转整数；仅在本次传入时覆盖
        Integer visibilityCode = null;
        if (StrUtil.isNotBlank(dto.getVisibility())) {
            visibilityCode = PostVisibilityEnum.codeOfText(dto.getVisibility());
            ThrowUtils.throwIf(visibilityCode == null, ErrorCode.PARAMS_ERROR, "非法的可见性：" + dto.getVisibility());
        }

        LambdaUpdateWrapper<Post> uw = new LambdaUpdateWrapper<>();
        uw.eq(Post::getId, post.getId())
          .set(title != null, Post::getTitle, title)
          .set(content != null, Post::getContent, content)
          .set(dto.getCover() != null, Post::getCover, dto.getCover())
          .set(topic != null, Post::getTopic, topic)
          .set(visibilityCode != null, Post::getVisibility, visibilityCode)
          .set(Post::getStatus, PostStatusEnum.REVIEWING.getCode())
          .set(Post::getAuditStatus, PostAuditStatusEnum.PENDING.getCode());
        boolean updated = update(uw);
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "编辑失败");

        // 图片 / 标签写入「审核中」版本：只替换该状态下的记录，
        // 已发布版本的图片/标签原样保留，审核通过时再由 migrateReviewingRelations 整体顶替
        if (dto.getImageUrl() != null) {
            rebuildImages(post.getId(), dto.getImageUrl(), PostStatusEnum.REVIEWING.getCode());
        }
        if (topicTags != null) {
            rebuildTags(post.getId(), topicTags, PostStatusEnum.REVIEWING.getCode());
        }
        return ResultUtils.success("修改成功，已提交审核");
    }

    /**
     * 设置帖子可见性（作者本人操作）。前端传文字（"公开"/"仅粉丝可见"/"私密"），经枚举转整数。
     *
     * <p>只改 visibility，<b>不触碰 status / auditStatus</b>：生命周期由审核流单向推进，
     * 作者能自由支配的只有「谁能看到我的帖子」这一维度。</p>
     */
    @Override
    public void updatePostVisibility(Long id, String visibilityText) {
        long loginId = StpUtil.getLoginIdAsLong();

        ThrowUtils.throwIf(id == null || StrUtil.isBlank(visibilityText), ErrorCode.PARAMS_ERROR, "参数不能为空");
        // 文字 → 枚举（非法文字直接拒绝，不落脏数据）
        PostVisibilityEnum target = PostVisibilityEnum.getByText(visibilityText);
        ThrowUtils.throwIf(target == null, ErrorCode.PARAMS_ERROR, "非法的可见性：" + visibilityText);

        Post post = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        ThrowUtils.throwIf(!Objects.equals(post.getUserId(), loginId), ErrorCode.NO_AUTH_ERROR, "只能操作自己的帖子");

        // 幂等：已是目标可见性，直接返回（避免 update 影响 0 行被误判为「设置失败」）
        if (target.matches(post.getVisibility())) {
            return;
        }

        boolean updated = lambdaUpdate()
                .eq(Post::getId, id)
                .set(Post::getVisibility, target.getCode())
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "设置可见性失败");
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
            CountUtils.increment(boardService, Board::getId, post.getBoardId(), "postCount", -1);
        }
        //4.作者的作品数减少一
        CountUtils.increment(userService, User::getId, loginId, "postCount", -1);
    }

    /**
     * 管理员审核帖子（推进状态 + 迁移「审核中」版本的图片/标签）。
     *
     * <p>「先审后发」语义下，待审内容在 createPost / updatePost 时<b>已经直接写入正式字段</b>
     * （同时把帖子置为「审核中」，审核期间前台不展示），因此审核环节不存在需要"应用"的影子内容，
     * 只需推进状态、并把 status=审核中 的图片/标签整体迁移为最终状态。</p>
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void auditPost(Long id, Boolean pass) {
        ThrowUtils.throwIf(id == null || pass == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        Post post = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        // 仅「审核中」的帖子需要审核：避免重复审核，也避免误操作草稿/已发布/已下架的帖子
        ThrowUtils.throwIf(!PostStatusEnum.REVIEWING.matches(post.getStatus()),
                ErrorCode.OPERATION_ERROR,
                "该帖子当前不处于「" + PostStatusEnum.REVIEWING.getText() + "」，无需审核");

        // 通过 → 已发布；
        // 驳回 → 下架：内容已覆盖正式字段，没有旧版本可回退，不能对外展示（作者可修改后重新提交）
        PostStatusEnum finalStatus = pass ? PostStatusEnum.PUBLISHED : PostStatusEnum.OFFLINE;
        PostAuditStatusEnum auditResult = pass ? PostAuditStatusEnum.APPROVED : PostAuditStatusEnum.REJECTED;

        // 带上 status=审核中 作为更新条件：并发重复审核时只有一次能生效
        boolean updated = lambdaUpdate()
                .eq(Post::getId, id)
                .eq(Post::getStatus, PostStatusEnum.REVIEWING.getCode())
                .set(Post::getStatus, finalStatus.getCode())
                .set(Post::getAuditStatus, auditResult.getCode())
                .update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "审核操作失败，请刷新后重试");

        // 「审核中」版本的图片/标签整体迁移为最终状态，并清理该帖其它历史版本（标签同步回退 useCount）
        migrateReviewingRelations(id, finalStatus.getCode());

        // 注意：板块帖子数在 createPost 时已 +1（审核中/驳回也算帖子），此处不再累加。
        // 否则「创建通过」会加两次，且每次编辑重新送审通过都会再 +1。
    }


    // ==================== 私有工具方法 ====================

    /**
     * 实体 → 对外展示 VO。
     *
     * <p>三个枚举字段（visibility / status / auditStatus）在实体里是整数、在 VO 里是文字，
     * 类型不一致，必须从 {@code copyProperties} 中排除后手工转换，否则 Hutool 会把
     * 整数直接转成 "1"/"3" 这类无意义字符串；topic 由逗号串解析为 id 列表；
     * imageUrl 从 post_image 表按<b>帖子当前状态</b>回读（版本化存储，见类头注释）。</p>
     *
     * @param post 帖子实体（null 返回 null）
     * @return 装配好的 VO
     */
    private PostVO buildPostVO(Post post) {
        if (post == null) {
            return null;
        }
        PostVO vo = BeanUtil.copyProperties(post, PostVO.class,
                "topic", "visibility", "status", "auditStatus");
        vo.setTopic(parseTopicIds(post.getTopic()));
        vo.setVisibility(PostVisibilityEnum.textOfCode(post.getVisibility()));
        vo.setStatus(PostStatusEnum.textOfCode(post.getStatus()));
        vo.setAuditStatus(PostAuditStatusEnum.textOfCode(post.getAuditStatus()));
        vo.setImageUrl(loadImageUrls(post.getId(), post.getStatus()));
        return vo;
    }

    /**
     * 解析 post.topic（逗号分隔的 tag id 串）为 id 列表。
     * 非数字片段直接跳过，兼容历史上可能存过标签名称的脏数据。
     */
    private List<Long> parseTopicIds(String topic) {
        if (StrUtil.isBlank(topic)) {
            return List.of();
        }
        return StrUtil.split(topic, ',').stream()
                .map(StrUtil::trim)
                .filter(NumberUtil::isLong)
                .map(Long::valueOf)
                .collect(Collectors.toList());
    }

    /**
     * 回读指定版本状态下的图片 URL（按 sort 升序）。
     *
     * @param postId 帖子内部 id
     * @param status 版本状态（已发布 / 草稿 / 审核中），与帖子当前 status 一致
     */
    private List<String> loadImageUrls(Long postId, Integer status) {
        if (postId == null || status == null) {
            return List.of();
        }
        return postImageService.lambdaQuery()
                .eq(PostImage::getPostId, postId)
                .eq(PostImage::getStatus, status)
                .orderByAsc(PostImage::getSort)
                .list().stream()
                .map(PostImage::getUrl)
                .collect(Collectors.toList());
    }

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
     *
     * @param status 该批图片所属的帖子版本状态（已发布 / 草稿 / 审核中），与写入时帖子的目标状态一致
     */
    private void saveImages(Long postId, List<String> images, Integer status) {
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
            pi.setStatus(status);
            list.add(pi);
        }
        if (!list.isEmpty()) {
            postImageService.saveBatch(list);
        }
    }

    /**
     * 重建<b>指定状态下</b>的图片：先查该帖子在该状态下是否已有记录，
     * 没有则直接插入新的；已有则同状态删旧插新（全量替换），<b>其它状态的图片不受影响</b>。
     *
     * @param status 目标版本状态（已发布 / 草稿 / 审核中）
     */
    private void rebuildImages(Long postId, List<String> images, Integer status) {
        // 先查看该帖子在该状态下有没有图片
        List<PostImage> exist = postImageService.lambdaQuery()
                .eq(PostImage::getPostId, postId)
                .eq(PostImage::getStatus, status)
                .list();
        if (exist.isEmpty()) {
            // 没有该状态的记录 → 直接插入新的
            saveImages(postId, images, status);
            return;
        }
        // 已存在该状态的记录 → 仅删除同状态旧记录，再插入新的
        postImageService.remove(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, postId)
                .eq(PostImage::getStatus, status));
        saveImages(postId, images, status);
    }

    /**
     * 校验并解析标签：前端传 tag id 列表 → 查 tag 表（<b>仅 status=1 未被禁用</b>）。
     * 传入了不存在或被禁用的 id 时抛参数错误，防止引用失效/违规标签。
     *
     * @param tagIds 前端传入的 tag id 列表（可为 null/空）
     * @return 解析后的有效标签列表（按传入顺序）
     */
    private List<Tag> resolveTags(List<Long> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        List<Long> distinctIds = tagIds.stream().distinct().toList();
        List<Tag> tags = tagService.lambdaQuery()
                .in(Tag::getId, distinctIds)
                .eq(Tag::getStatus, 1)   // 仅未被禁用的标签
                .list();
        ThrowUtils.throwIf(tags.size() != distinctIds.size(),
                ErrorCode.PARAMS_ERROR, "包含无效或已禁用的标签");
        return tags;
    }

    /**
     * 标签名称列表转存储id字符
     */
    private String joinTagNames(List<Tag> tags) {
        return tags.stream().map(tag -> StrUtil.toString(tag.getId())).collect(Collectors.joining(","));
    }

    /**
     * 绑定标签关联：useCount+1 + 写 post_tag（唯一键 (postId,tagId,status) 防重）。
     *
     * @param status 该批关联所属的帖子版本状态（已发布 / 草稿 / 审核中）
     */
    private void bindTags(Long postId, List<Tag> tags, Integer status) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        for (Tag tag : tags) {
            PostTag pt = new PostTag();
            pt.setPostId(postId);
            pt.setTagId(tag.getId());
            pt.setStatus(status);
            try {
                postTagService.save(pt);
                // 仅真正新增关联时才累加使用次数，避免重复关联把 useCount 刷高
                tagService.lambdaUpdate().eq(Tag::getId, tag.getId())
                        .setSql("useCount = useCount + 1").update();
            } catch (DuplicateKeyException e) {
                // 同状态下已关联过，忽略（useCount 也不重复累加）
            }
        }
    }

    /**
     * 重建<b>指定状态下</b>的标签关联：先查该帖子在该状态下是否已有关联，
     * 没有则直接绑定新的；已有则同状态删旧（useCount 回退）再绑新，<b>其它状态的关联不受影响</b>。
     *
     * @param status 目标版本状态（已发布 / 草稿 / 审核中）
     */
    private void rebuildTags(Long postId, List<Tag> tags, Integer status) {
        // 先查看该帖子在该状态下有没有标签关联
        List<PostTag> old = postTagService.lambdaQuery()
                .eq(PostTag::getPostId, postId)
                .eq(PostTag::getStatus, status)
                .list();
        if (old.isEmpty()) {
            // 没有该状态的关联 → 直接绑定新的
            bindTags(postId, tags, status);
            return;
        }
        // 已存在该状态的关联 → 旧标签 useCount 回退后删除同状态关联，再绑新
        for (PostTag pt : old) {
            tagService.lambdaUpdate().eq(Tag::getId, pt.getTagId())
                    .setSql("useCount = useCount - 1").update();
        }
        postTagService.remove(new LambdaQueryWrapper<PostTag>()
                .eq(PostTag::getPostId, postId)
                .eq(PostTag::getStatus, status));
        bindTags(postId, tags, status);
    }

    /**
     * 审核结束后迁移关联数据：把该帖「审核中」版本的图片/标签整体迁移为最终状态。
     *
     * <p>迁移前会清掉该帖<b>除「审核中」以外的所有历史版本</b>（标签同步回退 useCount）：
     * 「先审后发」下正式字段已被新内容覆盖，旧版本的图片/标签不再对应任何内容，
     * 若只清最终状态那一份，驳回（审核中→下架）时旧「已发布版」就会残留成孤儿数据并长期占着 useCount。
     * 清理后可保证：审核结束时该帖只剩一套与帖子状态一致的图片/标签。</p>
     *
     * @param postId      帖子内部 id
     * @param finalStatus 审核后的最终状态（通过=已发布；驳回=下架）
     */
    private void migrateReviewingRelations(Long postId, Integer finalStatus) {
        Integer reviewing = PostStatusEnum.REVIEWING.getCode();
        if (Objects.equals(reviewing, finalStatus)) {
            return;
        }
        // 1. 图片：清掉其它版本（含 status 为 null 的历史脏数据），再把审核中版本迁移过去
        postImageService.remove(new LambdaQueryWrapper<PostImage>()
                .eq(PostImage::getPostId, postId)
                .and(w -> w.ne(PostImage::getStatus, reviewing).or().isNull(PostImage::getStatus)));
        postImageService.lambdaUpdate()
                .eq(PostImage::getPostId, postId)
                .eq(PostImage::getStatus, reviewing)
                .set(PostImage::getStatus, finalStatus)
                .update();

        // 2. 标签：其它版本先回退 useCount 再删除，最后把审核中版本迁移过去
        List<PostTag> staleTags = postTagService.lambdaQuery()
                .eq(PostTag::getPostId, postId)
                .and(w -> w.ne(PostTag::getStatus, reviewing).or().isNull(PostTag::getStatus))
                .list();
        if (!staleTags.isEmpty()) {
            for (PostTag pt : staleTags) {
                tagService.lambdaUpdate().eq(Tag::getId, pt.getTagId())
                        .setSql("useCount = useCount - 1").update();
            }
            postTagService.remove(new LambdaQueryWrapper<PostTag>()
                    .eq(PostTag::getPostId, postId)
                    .and(w -> w.ne(PostTag::getStatus, reviewing).or().isNull(PostTag::getStatus)));
        }
        postTagService.lambdaUpdate()
                .eq(PostTag::getPostId, postId)
                .eq(PostTag::getStatus, reviewing)
                .set(PostTag::getStatus, finalStatus)
                .update();
    }
}
