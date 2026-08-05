package com.ruwei.controller;


import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.empty.Tag;
import com.ruwei.service.TagService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 标签（话题）相关接口：热门标签榜等。
 * 公开接口（无需登录），与文档 02-content-module.md 5.3 TagController 对齐。
 */
@RestController
@RequestMapping("/tag")
public class TagController {

    @Resource
    private TagService tagService;

    /**
     * 热门标签榜（按使用次数倒序，取前 20）
     */
    @GetMapping("/hot")
    public BaseResponse<List<Tag>> getHotTags() {
        return ResultUtils.success(tagService.getHotTags(20));
    }


}
