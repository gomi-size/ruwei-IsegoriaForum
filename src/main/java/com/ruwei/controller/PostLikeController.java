package com.ruwei.controller;

import com.ruwei.common.BaseResponse;
import com.ruwei.domain.empty.PostLike;
import com.ruwei.service.PostLikeService;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/postLike")
@RestController
public class PostLikeController {


    @Resource
    private PostLikeService postLikeService;


    @PostMapping("/add")
    public BaseResponse<String> addPostLike(){
        return null;
    }
}
