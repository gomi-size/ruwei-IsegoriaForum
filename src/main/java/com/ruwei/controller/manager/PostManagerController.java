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
 * <p>审核约定：创建送审（status=3+auditStatus=1）；编辑<b>先审后发</b>（新内容直接覆盖正式字段并重新置为
 * 审核中，审核期间不对外展示）；审核只推进状态，不搬运内容。</p>
 */
@RestController
@RequestMapping("/adminPost")
@SaCheckLogin
@SaCheckRole("admin")
public class PostManagerController {

    @Resource
    private PostService postService;

    /**
     * 管理员审核帖子（仅对 status=3 审核中 的帖子有效）
     * pass：true 通过 → 已发布 / false 驳回 → 下架
     */
    @PostMapping("/audit")
    public BaseResponse<String> auditPost(@RequestParam Long id, @RequestParam Boolean pass) {
        postService.auditPost(id, pass);
        return ResultUtils.success(pass ? "审核通过" : "已驳回");
    }


}
