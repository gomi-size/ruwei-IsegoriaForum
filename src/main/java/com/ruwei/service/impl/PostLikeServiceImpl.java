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





}




