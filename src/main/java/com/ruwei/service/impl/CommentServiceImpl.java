package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.Comment;
import com.ruwei.service.CommentService;
import com.ruwei.mapper.CommentMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【comment(评论表(两级盖楼))】的数据库操作Service实现
* @createDate 2026-08-14 16:44:41
*/
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment>
    implements CommentService{

}




