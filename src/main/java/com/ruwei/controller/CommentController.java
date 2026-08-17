package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.ruwei.common.BaseResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/comment")
@SaCheckLogin
public class CommentController {


    /**
     * 评论（帖子评论）
     */
    @PostMapping("/postComment")
    public BaseResponse<String> postComment(){

        return  null;
    }

}
