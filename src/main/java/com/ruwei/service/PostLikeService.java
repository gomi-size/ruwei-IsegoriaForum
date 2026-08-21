package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.PostLikeDTO;
import com.ruwei.domain.empty.PostLike;


/**
* @author Administrator
* @description 针对表【post_like(帖子点赞表)】的数据库操作Service
* @createDate 2026-08-20 13:54:13
*/
public interface PostLikeService extends IService<PostLike> {

    /**
     * 点赞和取消点赞
     * @param postLikeDTO
     */
    void PostLike(PostLikeDTO postLikeDTO);
}
