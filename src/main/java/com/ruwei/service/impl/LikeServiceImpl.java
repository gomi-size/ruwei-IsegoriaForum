package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.LikePersistMessage;
import com.ruwei.domain.empty.Comment;
import com.ruwei.domain.empty.CommentLike;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.PostLike;
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
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Date;
import java.util.Map;

@Slf4j
@Service
public class LikeServiceImpl implements LikeService {

    @Resource
    private PostService postService;
    @Resource
    private CommentService commentService;
    @Resource
    private LikeCacheManager likeCacheManager;
    @Resource
    private FollowCacheManager followCacheManager;
    @Resource
    private PostLikeMapper postLikeMapper;
    @Resource
    private CommentLikeMapper commentLikeMapper;
    @Resource
    private RabbitTemplate rabbitTemplate;
    @Resource
    private ApplicationEventPublisher eventPublisher;

    /** 降级总开关：true 时全链路走 DB（11 §12 降级开关） */
    @Value("${like.fallback-to-db:false}")
    private boolean fallbackToDb;




    @Override
    public LikeToggleVO togglePostLike(String postCode) {

        long loginId = StpUtil.getLoginIdAsLong();
        Post post = postService.lambdaQuery().eq(Post::getPostCode, postCode).one();
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        // 只有「已发布 + 审核通过」可点赞
        ThrowUtils.throwIf(!PostStatusEnum.PUBLISHED.matches(post.getStatus())
                        || !PostAuditStatusEnum.APPROVED.matches(post.getAuditStatus()),
                ErrorCode.OPERATION_ERROR, "该帖子当前不可点赞");
        //可见性
        checkVisibility(post, loginId);
        Long postId = post.getId();
        if(fallbackToDb){
            return directTogglePostDb(postId, loginId, post.getUserId());
        }
        LikeCacheManager.ToggleResult r = likeCacheManager.togglePostLike(postId, loginId);
        sendMq(1, postId, loginId, r.action());

        return null;
    }

    @Override
    public LikeToggleVO toggleCommentLike(Long commentId) {
        return null;
    }

    @Override
    public LikeToggleVO getPostLikeStatus(String postCode) {
        return null;
    }

    @Override
    public Long getPostLikeCount(String postCode) {
        return 0L;
    }

    @Override
    public Map<Long, Boolean> batchPostLiked(Collection<Long> postIds, Long loginId) {
        return Map.of();
    }





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

    //降级处理，如果redis和Mq不可用那就走
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


    //降级处理，如果redis和Mq不可用那就走
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
