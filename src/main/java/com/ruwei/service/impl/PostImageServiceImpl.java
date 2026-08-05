package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.PostImage;
import com.ruwei.service.PostImageService;
import com.ruwei.mapper.PostImageMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【post_image(帖子图片表)】的数据库操作Service实现
* @createDate 2026-08-05 10:22:22
*/
@Service
public class PostImageServiceImpl extends ServiceImpl<PostImageMapper, PostImage>
    implements PostImageService{

}




