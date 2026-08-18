package com.ruwei.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.CommentAddDTO;
import com.ruwei.domain.dto.CommentPageDTO;
import com.ruwei.domain.dto.CommentReplyPageDTO;
import com.ruwei.domain.empty.Comment;
import com.ruwei.domain.vo.CommentVO;


/**
* @author Administrator
* @description 针对表【comment(评论表(两级盖楼))】的数据库操作Service
* @createDate 2026-08-14 16:44:41
*/
public interface CommentService extends IService<Comment> {

    /**
     * 添加评论
     * @param commentAddDTO
     */
    void addComment(CommentAddDTO commentAddDTO);

    /**
     * 删除评论
     * @param commentId
     */
    void deleteComment(Long commentId);

    /**
     * 帖子评论列表（一级评论分页 + 每条挂前 2 条二级回复，防 N+1）
     * @param dto 分页参数 + postCode
     */
    IPage<CommentVO> listByPost(CommentPageDTO dto);

    /**
     * 某顶层评论的全部回复分页（二级回复按创建时间正序，防 N+1）
     * @param dto 分页参数 + 顶层评论 commentId
     */
    IPage<CommentVO> listReplies(CommentReplyPageDTO dto);
}
