package com.ruwei.mapper;

import com.ruwei.domain.empty.CommentLike;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
* @author Administrator
* @description 针对表【comment_like(评论点赞表)】的数据库操作Mapper
* @createDate 2026-08-18
* @Entity com.ruwei.domain.empty.CommentLike
*/
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {

}
