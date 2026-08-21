package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.PostLikeDTO;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.PostLike;
import com.ruwei.manager.FollowCacheManager;
import com.ruwei.service.PostLikeService;
import com.ruwei.mapper.PostLikeMapper;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【post_like(帖子点赞表)】的数据库操作Service实现
* @createDate 2026-08-20 13:54:13
*/
@Service
public class PostLikeServiceImpl extends ServiceImpl<PostLikeMapper, PostLike>
    implements PostLikeService{

    @Resource
    private PostService postService;
    @Resource
    private FollowCacheManager followCacheManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 点赞和取消点赞
     * @param postLikeDTO
     */
    @Override
    public void PostLike(PostLikeDTO postLikeDTO) {
        //取出参数
        long loginId = StpUtil.getLoginIdAsLong();
        Integer status = postLikeDTO.getStatus();
        Long postId = postLikeDTO.getPostId();

        //鉴权
        //进行校验，只有帖子存在，并且发布了才能进行点赞
        Post post = postService.lambdaQuery().eq(Post::getId, postId)
                .eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode()).one();
        ThrowUtils.throwIf(BeanUtil.isEmpty(post), ErrorCode.NOT_FOUND_ERROR,"目标帖子不存在");
        if(PostVisibilityEnum.PRIVATE.matches(post.getVisibility())){
            ThrowUtils.throwIf(!post.getUserId().equals(loginId),ErrorCode.NO_AUTH_ERROR,"该帖子为私密帖子");
        }
        //如果是粉丝可见那么点赞就必须是粉丝
        if(PostVisibilityEnum.FANS_ONLY.matches(post.getAuditStatus())){
            Boolean following = followCacheManager.isFollowing(loginId, post.getUserId());
            ThrowUtils.throwIf(!following,ErrorCode.NO_AUTH_ERROR,"只有关注该作者才能点赞");
        }
        if(status==0){
            addPostLike(loginId,postId);
        }

    }

    /**
     * 点赞
     * @param loginId
     * @param postId
     */
    private void addPostLike(long loginId, Long postId) {

        //先查询是不是点赞了？


    }




}




