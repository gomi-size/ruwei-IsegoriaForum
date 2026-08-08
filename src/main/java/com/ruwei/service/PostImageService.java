package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.empty.PostImage;
import com.ruwei.domain.vo.ImageUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 帖子图片服务
 */
public interface PostImageService extends IService<PostImage> {

    /**
     * 上传帖子图片，返回可直接填充 ContentBlock 的上传结果。
     *
     * @param file 上传的图片文件
     * @return 图片上传结果
     */
    ImageUploadVO uploadImage(MultipartFile file);
}
