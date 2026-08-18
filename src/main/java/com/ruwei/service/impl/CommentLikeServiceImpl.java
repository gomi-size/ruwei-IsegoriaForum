package com.ruwei.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.CommentLike;
import com.ruwei.mapper.CommentLikeMapper;
import com.ruwei.service.CommentLikeService;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【comment_like(评论点赞表)】的数据库操作Service实现
* @createDate 2026-08-18
*/
@Service
public class CommentLikeServiceImpl extends ServiceImpl<CommentLikeMapper, CommentLike>
    implements CommentLikeService {

}
