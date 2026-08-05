package com.ruwei.controller.manager;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.PostDTO;
import com.ruwei.domain.empty.Post;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子（内容）相关接口
 *
 * <p>审核约定：创建送审（status=3+auditStatus=1）；编辑先发后审（新内容暂存 pending，
 * 旧内容继续展示）；设置状态不走审核；删除为逻辑删除（isDelete=1）。</p>
 */
@RestController
@RequestMapping("/adminPost")
@SaCheckLogin
@SaCheckRole("admin")
public class PostManagerController {

    @Resource
    private PostService postService;

    /**
     * 管理员审核帖子（通过=应用待审内容并发布；驳回=丢弃待审内容）
     * pass：true 通过 / false 驳回
     */
    @PostMapping("/audit")
    public BaseResponse<String> auditPost(@RequestParam Long id, @RequestParam Boolean pass) {
        postService.auditPost(id, pass);
        return ResultUtils.success(pass ? "审核通过" : "已驳回");
    }
}
