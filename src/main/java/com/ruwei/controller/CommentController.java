package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.CommentAddDTO;
import com.ruwei.service.CommentService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comment")
@SaCheckLogin
public class CommentController {

    @Resource
    private CommentService commentService;


    /**
     * 评论（帖子评论）
     */
    @PostMapping("/postComment")
    public BaseResponse<String> addComment(@RequestBody CommentAddDTO commentAddDTO){

        ThrowUtils.throwIf(BeanUtil.isEmpty(commentAddDTO)
                ||commentAddDTO.getPostCode()==null
                || StrUtil.isBlank(commentAddDTO.getContent()),
                ErrorCode.PARAMS_ERROR,"帖子id或者内容为空");
        commentService.addComment(commentAddDTO);

        return ResultUtils.success("评论成功");
    }

}
