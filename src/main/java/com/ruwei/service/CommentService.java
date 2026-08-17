package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.CommentAddDTO;
import com.ruwei.domain.empty.Comment;


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

}
