package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.vo.ImageUploadVO;
import com.ruwei.service.PostImageService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/postImage")
@SaCheckLogin
public class PostImageController {

    @Resource
    private PostImageService postImageService;

    /**
     * 上传帖子图片。
     */
    @PostMapping("/upload")
    public BaseResponse<ImageUploadVO> uploadImage(@RequestParam("file") MultipartFile file) {
        return ResultUtils.success(postImageService.uploadImage(file));
    }


}
