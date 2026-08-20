package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.PostLike;
import com.ruwei.service.PostLikeService;
import com.ruwei.mapper.PostLikeMapper;
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




