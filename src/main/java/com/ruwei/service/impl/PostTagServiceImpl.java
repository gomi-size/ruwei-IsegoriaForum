package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.PostTag;
import com.ruwei.service.PostTagService;
import com.ruwei.mapper.PostTagMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【post_tag(帖子标签关联表)】的数据库操作Service实现
* @createDate 2026-08-05 10:25:17
*/
@Service
public class PostTagServiceImpl extends ServiceImpl<PostTagMapper, PostTag>
    implements PostTagService{

}




