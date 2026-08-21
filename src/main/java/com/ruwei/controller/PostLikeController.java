package com.ruwei.controller;

import cn.hutool.core.bean.BeanUtil;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.PostLikeDTO;
import com.ruwei.domain.empty.PostLike;
import com.ruwei.service.PostLikeService;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/postLike")
@RestController
public class PostLikeController {


    @Resource
    private PostLikeService postLikeService;


    /**
     * 点赞和取消点赞都使用同一个接口
     * @return
     */
    @PostMapping("/like")
    public BaseResponse<String> PostLike(@RequestBody PostLikeDTO postLikeDTO){
        ThrowUtils.throwIf(BeanUtil.isEmpty(postLikeDTO)
                ||postLikeDTO.getPostId()==null
                ||postLikeDTO.getStatus()==null, ErrorCode.PARAMS_ERROR,"请求参数不能为空");
        postLikeService.PostLike(postLikeDTO);
        return null;
    }
}
