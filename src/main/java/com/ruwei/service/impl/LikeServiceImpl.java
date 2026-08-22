package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.UUID;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.component.notification.event.LikeEvent;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.LikePersistMessage;
import com.ruwei.domain.empty.*;
import com.ruwei.domain.utils.CountUtils;
import com.ruwei.domain.vo.LikeToggleVO;
import com.ruwei.manager.FollowCacheManager;
import com.ruwei.manager.LikeCacheManager;
import com.ruwei.mapper.CommentLikeMapper;
import com.ruwei.mapper.PostLikeMapper;
import com.ruwei.service.CommentService;
import com.ruwei.service.LikeService;
import com.ruwei.service.PostService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 点赞业务实现（实施指南 13-like-module-impl.md §5.4，设计依据 docs/modules/11-like-module.md）。
 *
 * <p>核心链路：<b>Redis 先行（Lua 原子 toggle）→ MQ 异步落库（带真实 action）→ LikeEvent 通知</b>，
 * 与 {@link LikeCacheManager}（Redis 热索引 + 原子翻转）、LikePersistConsumer（MQ 消费者幂等落库）分层协作：
 * <ul>
 *   <li><b>写路径（toggle）</b>：帖子/评论存在性 + 状态 + 可见性校验 →
 *       {@link LikeCacheManager#togglePostLike} Lua 原子翻转（关系 Set 增删 + 计数 INCR/DECR 同脚本完成）→
 *       {@link #sendMq} 异步落库 → 点赞且非自赞时发布 {@link LikeEvent}（@Async 监听发 type=1 通知，11 §10）；</li>
 *   <li><b>读路径</b>：是否赞过 / 点赞计数一律取自 Redis（键缺失由 LikeCacheManager 回源 DB 懒加载重建，
 *       空集哨兵防空穿透）；列表批量走 pipeline 一次往返（11 §9.3）；</li>
 *   <li><b>降级（11 §12）</b>：{@code like.fallback-to-db=true} 时全链路走 DB；Redis 不可用自动降纯 DB 路径；
 *       MQ 发送异常 / confirm 失败时同步直写 DB，保证功能不挂。</li>
 * </ul>
 *
 * <p>约定：点赞者 id 一律服务端取（StpUtil），不信任前端；Redis 键与集合元素一律内部主键（与 post_like /
 * 通知模块口径统一，防串号）；计数增减统一走 {@link CountUtils} 原子 SQL，杜绝读改写竞态。</p>
 *
 * @see com.ruwei.manager.LikeCacheManager
 * @see com.ruwei.domain.dto.LikePersistMessage
 * @see com.ruwei.domain.empty.LikeCorrelationData
 */
@Slf4j
@Service
public class LikeServiceImpl implements LikeService {

    /** 帖子 Service：postCode→Post 查询、likeCount 原子增减（CountUtils） */
    @Resource
    private PostService postService;
    /** 评论 Service：commentId 存在性/状态校验、likeCount 原子增减 */
    @Resource
    private CommentService commentService;
    /** 点赞 Redis 热索引 + Lua 原子 toggle 管理器（关系 Set + 计数键） */
    @Resource
    private LikeCacheManager likeCacheManager;
    /** 关注热索引：FANS_ONLY 可见性校验复用其 isFollowing */
    @Resource
    private FollowCacheManager followCacheManager;
    /** 帖子点赞关系表（DB 最终真相，MQ 消费者落库 / 降级直写 / 回源重建） */
    @Resource
    private PostLikeMapper postLikeMapper;
    /** 评论点赞关系表 */
    @Resource
    private CommentLikeMapper commentLikeMapper;
    /** RabbitTemplate：发 like.exchange（rk=like.post / like.comment）异步落库 */
    @Resource
    private RabbitTemplate rabbitTemplate;
    /** 应用事件发布器：点赞成功且非自赞时发布 LikeEvent（11 §10） */
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /** 降级总开关：true 时全链路走 DB（11 §12 降级开关） */
    @Value("${like.fallback-to-db:false}")
    private boolean fallbackToDb;

    /**
     * 注册 RabbitMQ publisher-confirm 回调（{@code @PostConstruct}，应用启动时执行一次）。
     *
     * <p>confirm 失败（{@code ack=false}，broker 拒收 / 路由失败）时，从自定义 {@link LikeCorrelationData}
     * 中取出发送前携带的原始 {@link LikePersistMessage}，走 {@link #directPersist} 同步直写 DB 兜底
     * （11 §9.1 ⑥）。不使用 {@code CorrelationData.getReturned()}：它只返回 Spring AMQP 的
     * {@code ReturnedMessage} 包装，还原不了原始业务对象。</p>
     *
     * <p>前提：{@code application.yml} 已配 {@code spring.rabbitmq.publisher-confirm-type: correlated}，
     * 否则回调不会被触发。</p>
     */
    @PostConstruct
    public void initConfirmCallback() {
        // MQ confirm 失败 → 异步 best-effort 降级直写 DB（11 §9.1 ⑥）
        rabbitTemplate.setConfirmCallback((correlationData, ack, cause) -> {
            if (!ack && correlationData != null) {
                if (correlationData instanceof LikeCorrelationData likeData) {
                    // 直接取出原始的 Java 对象
                    LikePersistMessage msg = likeData.getMessage();
                    log.warn("点赞 MQ 确认失败，降级直写 DB eventId={} cause={}", msg.getEventId(), cause);
                    directPersist(msg);
                }
            }
        });
    }

    // ===================== 帖子点赞 =====================

    /**
     * 帖子点赞 toggle（11 §9.1 ①~⑧）。无状态翻转：前端不传 action，后端查当前状态并取反。
     *
     * <p>流程：postCode → postId 解析 → 帖子存在 + PUBLISHED + APPROVED 校验 →
     * 可见性校验（PRIVATE 仅作者 / FANS_ONLY 必须已关注，复用 FollowCacheManager）→
     * Redis Lua 原子 toggle → {@link #sendMq} 异步落库（带真实 action）→
     * action=1 且非自赞发布 {@link LikeEvent} → 返回 {isLiked, likeCount}（均来自 Redis）。</p>
     *
     * <p>降级：{@link #fallbackToDb} 开启或 Redis 不可用（{@link RedisConnectionFailureException}）时，
     * 走 {@link #directTogglePostDb} 纯 DB 路径（11 §12），功能不挂。</p>
     *
     * @param postCode 帖子业务编码（对外 String，内部解析为 postId）
     * @return 切换后状态：isLiked 是否已赞、likeCount 最新计数
     * on 帖子不存在（NOT_FOUND_ERROR）/ 未发布或未过审（OPERATION_ERROR）/
     *                           可见性不满足（NO_AUTH_ERROR）时抛出
     */
    @Override
    public LikeToggleVO togglePostLike(String postCode) {
        long loginId = StpUtil.getLoginIdAsLong();
        Post post = postService.lambdaQuery().eq(Post::getPostCode, postCode).one();
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        // 只有「已发布 + 审核通过」可点赞（11 §9.1 ②）
        ThrowUtils.throwIf(!PostStatusEnum.PUBLISHED.matches(post.getStatus())
                        || !PostAuditStatusEnum.APPROVED.matches(post.getAuditStatus()),
                ErrorCode.OPERATION_ERROR, "该帖子当前不可点赞");
        // 可见性（11 §9.1 ③）
        checkVisibility(post, loginId);
        Long postId = post.getId();
        if (fallbackToDb) {
            return directTogglePostDb(postId, loginId, post.getUserId());
        }
        try {
            // 进行点赞（Redis 原子 toggle）
            LikeCacheManager.ToggleResult r = likeCacheManager.togglePostLike(postId, loginId);
            sendMq(1, postId, loginId, r.action());
            // 点赞成功且非本人 → 发通知（11 §9.1 ⑦ / §10）
            if (r.action() == 1 && loginId != post.getUserId()) {
                eventPublisher.publishEvent(new LikeEvent(this, loginId, postId, post.getUserId()));
            }
            return new LikeToggleVO(r.action() == 1, r.count());
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis 不可用，降级纯 DB 路径 postId={}", postId);
            return directTogglePostDb(postId, loginId, post.getUserId());
        }
    }

    // ===================== 评论点赞 =====================

    /**
     * 评论点赞 toggle（11 §9.2）。语义同帖子，仅目标为评论。
     *
     * <p>已删除（status=2）的评论视为不存在，对齐 CommentService.deleteComment 口径。
     * 评论点赞本期不发布通知（11 §9.2 ⑥，后期再加）。</p>
     *
     * @param commentId 评论内部主键
     * @return 切换后状态：isLiked / likeCount
     * @throws Exception 评论不存在（NOT_FOUND_ERROR）时抛出
     */
    @Override
    public LikeToggleVO toggleCommentLike(Long commentId) {
        Long loginId = StpUtil.getLoginIdAsLong();
        Comment comment = commentService.getById(commentId);
        // 不存在或已删除(status=2) 视为不存在（对齐 CommentService.deleteComment 口径）
        ThrowUtils.throwIf(comment == null || comment.getStatus() == 2,
                ErrorCode.NOT_FOUND_ERROR, "评论不存在");

        try {
            if (fallbackToDb) {
                return directToggleCommentDb(commentId, loginId);
            }
            LikeCacheManager.ToggleResult r = likeCacheManager.toggleCommentLike(commentId, loginId);
            sendMq(2, commentId, loginId, r.action());
            // 评论点赞本期不发通知（后期加）
            return new LikeToggleVO(r.action() == 1, r.count());
        } catch (Exception ex) {
            log.warn("Redis 不可用，降级纯 DB 路径 commentId={}", commentId);
            return directToggleCommentDb(commentId, loginId);
        }
    }

    // ===================== 读：状态 / 计数 =====================

    /**
     * 查询「当前用户是否赞过 + 最新计数」（11 §9.3 读路径）。
     *
     * <p>状态来自 Redis 关系 Set（SISMEMBER），计数来自 Redis 计数键；键缺失时由
     * {@link LikeCacheManager} 回源 DB 懒加载重建，保证读路径不落 DB。</p>
     *
     * @param postCode 帖子业务编码
     * @return {isLiked 是否已赞, likeCount 最新计数}
     */
    @Override
    public LikeToggleVO getPostLikeStatus(String postCode) {
        long loginId = StpUtil.getLoginIdAsLong();
        Post post = postService.lambdaQuery().eq(Post::getPostCode, postCode).one();
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        boolean liked = likeCacheManager.isPostLiked(post.getId(), loginId);
        long count = likeCacheManager.getPostLikeCount(post.getId());
        LikeToggleVO vo = new LikeToggleVO();
        vo.setIsLiked(liked);
        vo.setLikeCount(count);
        return vo;
    }

    /**
     * 查询帖子点赞总数（Redis 优先，键缺失回源 DB 重建）。
     *
     * @param postCode 帖子业务编码
     * @return 点赞总数
     */
    @Override
    public Long getPostLikeCount(String postCode) {
        Post post = postService.lambdaQuery().eq(Post::getPostCode, postCode).one();
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        return likeCacheManager.getPostLikeCount(post.getId());
    }

    /**
     * 列表批量填充「是否已赞」（11 §9.3 / §13 末段，PostController 列表组装调用）。
     *
     * <p>Redis pipeline 一次往返完成，避免 N 次网络开销；Redis 不可用时降级 DB 逐条比对
     * （post_like 查重），功能不挂。</p>
     *
     * @param postIds 帖子内部主键集合（本页列表）
     * @param loginId 当前用户内部主键
     * @return postId → 是否赞过（缺失默认 false）
     */
    @Override
    public Map<Long, Boolean> batchPostLiked(Collection<Long> postIds, Long loginId) {
        try {
            return likeCacheManager.batchIsPostLiked(postIds, loginId);
        } catch (RedisConnectionFailureException ex) {
            log.warn("Redis 不可用，批量 isLiked 降级 DB");
            Map<Long, Boolean> map = new HashMap<>();
            for (Long pid : postIds) {
                boolean liked = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, pid).eq(PostLike::getUserId, loginId)) > 0;
                map.put(pid, liked);
            }
            return map;
        }
    }

    // ===================== 内部：可见性校验 =====================

    /**
     * 帖子可见性校验（11 §9.1 ③）：PRIVATE 仅作者可点赞；FANS_ONLY 必须关注作者
     * （复用 {@link FollowCacheManager#isFollowing} 热索引）；PUBLIC 无需校验。
     *
     * @param post    目标帖子（须含 visibility / userId）
     * @param loginId 当前登录用户内部主键
     */
    private void checkVisibility(Post post, long loginId) {
        Integer visibility = post.getVisibility();
        if (PostVisibilityEnum.PRIVATE.matches(visibility)) {
            ThrowUtils.throwIf(post.getUserId() != loginId, ErrorCode.NO_AUTH_ERROR, "私密帖子仅作者可点赞");
        } else if (PostVisibilityEnum.FANS_ONLY.matches(visibility)) {
            boolean following = Boolean.TRUE.equals(followCacheManager.isFollowing(loginId, post.getUserId()));
            ThrowUtils.throwIf(!following, ErrorCode.NO_AUTH_ERROR, "仅粉丝可点赞该帖子");
        }
        // PUBLIC 无需校验
    }

    // ===================== 内部：MQ 发送（带降级） =====================

    /**
     * 发送点赞异步落库消息（11 §9.1 ⑥）到 like.exchange（rk=like.post / like.comment）。
     *
     * <p>消息携带真实 action（1 赞 / 0 取，来自 Lua 返回值，勿硬编码），eventId 作为 MQ 消费幂等键
     * （消费者 SETNX 去重）。发送异常（broker 不可达等）时同步降级 {@link #directPersist} 直写 DB。
     * CorrelationData 使用自定义 {@link LikeCorrelationData} 携带原始消息，供 confirm 失败回调找回。</p>
     *
     * @param targetType 目标类型：1 帖子 / 2 评论
     * @param targetId   postId 或 commentId（内部主键）
     * @param userId     点赞者内部主键
     * @param action     动作：1 点赞 / 0 取消
     */
    private void sendMq(Integer targetType,Long targetId,Long userId,Integer action){
        //构建消息体
        LikePersistMessage msg = LikePersistMessage.builder()
                .eventId(UUID.randomUUID().toString())
                .targetType(targetType)
                .targetId(targetId)
                .userId(userId)
                .action(action)
                .timestamp(System.currentTimeMillis())
                .build();
        //发送消息
        try{
            LikeCorrelationData correlationData = new LikeCorrelationData(msg.getEventId(), msg);
            rabbitTemplate.convertAndSend("like.exchange",
                    targetType == 1 ? "like.post" : "like.comment",
                    msg,
                    correlationData
            );
            //降级处理
        }catch (Exception e){
                log.warn("点赞 MQ 发送异常，降级直写 DB targetId={}", targetId, e);
                directPersist(msg);
        }
    }

    // ===================== 内部：纯 DB 降级（Redis/MQ 不可用） =====================

    /**
     * 纯 DB 降级路径（11 §12）：Redis/MQ 不可用或 {@link #fallbackToDb} 开启时，
     * 直接对 post_like 表做 toggle。先查关系：不存在则插入并原子 +1 计数（新增且非自赞时同步发
     * {@link LikeEvent}）；已存在则删除并原子 -1。likeCount 最终值以 DB 表为准回读。
     *
     * @param postId     帖子内部主键
     * @param loginId    当前用户内部主键
     * @param postUserId 帖子作者内部主键（用于自赞判断）
     * @return 切换后状态：isLiked / likeCount
     */
    private LikeToggleVO directTogglePostDb(Long postId, Long loginId, Long postUserId) {
        LikeToggleVO vo = new LikeToggleVO();
        PostLike exist = postLikeMapper.selectOne(new LambdaQueryWrapper<PostLike>()
                .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, loginId));
        if (exist == null) {
            PostLike record = new PostLike();
            record.setPostId(postId);
            record.setUserId(loginId);
            record.setCreatedAt(new Date());
            postLikeMapper.insert(record);
            CountUtils.increment(postService, Post::getId, postId, "likeCount", 1);
            vo.setIsLiked(true);
            if (loginId != postUserId) {
                eventPublisher.publishEvent(new LikeEvent(this, loginId, postId, postUserId));
            }
        } else {
            postLikeMapper.deleteById(exist.getId());
            CountUtils.increment(postService, Post::getId, postId, "likeCount", -1);
            vo.setIsLiked(false);
        }
        vo.setLikeCount((long) (postService.getById(postId).getLikeCount()));
        return vo;
    }

    /**
     * 纯 DB 降级路径（11 §12）：对 comment_like 表做 toggle。评论点赞不发通知。
     *
     * @param commentId 评论内部主键
     * @param loginId   当前用户内部主键
     * @return 切换后状态：isLiked / likeCount
     */
    private LikeToggleVO directToggleCommentDb(Long commentId, Long loginId) {
        LikeToggleVO vo = new LikeToggleVO();
        CommentLike exist = commentLikeMapper.selectOne(new LambdaQueryWrapper<CommentLike>()
                .eq(CommentLike::getCommentId, commentId).eq(CommentLike::getUserId, loginId));
        if (exist == null) {
            CommentLike record = new CommentLike();
            record.setCommentId(commentId);
            record.setUserId(loginId);
            record.setCreatedAt(new Date());
            commentLikeMapper.insert(record);
            CountUtils.increment(commentService, Comment::getId, commentId, "likeCount", 1);
            vo.setIsLiked(true);
        } else {
            commentLikeMapper.deleteById(exist.getId());
            CountUtils.increment(commentService, Comment::getId, commentId, "likeCount", -1);
            vo.setIsLiked(false);
        }
        vo.setLikeCount((long) (commentService.getById(commentId).getLikeCount()));
        return vo;
    }

    /**
     * MQ 兜底落库（11 §9.1 ⑥ / §8.1）：confirm 失败 / 发送异常时同步直写 DB。
     * 仅补「赞」记录（action=1）；取赞降级极少见，由 LikeReconcileJob 对账兜底。
     *
     * <p>幂等防护：先查关系存在性，已存在则跳过；插入成功（影响行数 &gt; 0）才原子 +1 计数，
     * 绝不盲加减，避免与消费者落库重复叠加。</p>
     *
     * @param msg 点赞消息体（含 targetType / targetId / userId）
     */
    private void directPersist(LikePersistMessage msg) {
        if (msg.getTargetType() == 1) {
            Long postId = msg.getTargetId();
            Long userId = msg.getUserId();
            PostLike exist = postLikeMapper.selectOne(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, userId));
            if (exist == null) {
                PostLike r = new PostLike();
                r.setPostId(postId); r.setUserId(userId); r.setCreatedAt(new Date());
                if (postLikeMapper.insert(r) > 0)
                    CountUtils.increment(postService, Post::getId, postId, "likeCount", 1);
            }
        } else {
            Long commentId = msg.getTargetId();
            Long userId = msg.getUserId();
            CommentLike exist = commentLikeMapper.selectOne(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getCommentId, commentId).eq(CommentLike::getUserId, userId));
            if (exist == null) {
                CommentLike r = new CommentLike();
                r.setCommentId(commentId); r.setUserId(userId); r.setCreatedAt(new Date());
                if (commentLikeMapper.insert(r) > 0)
                    CountUtils.increment(commentService, Comment::getId, commentId, "likeCount", 1);
            }
        }
    }
}
