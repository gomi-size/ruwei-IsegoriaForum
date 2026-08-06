package com.ruwei.controller.manager;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.dto.PostQueryDTO;
import com.ruwei.domain.vo.PostVO;
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
     * 对没有通过的稿子需要说明未通过的消息
     * pass：true 通过 → 已发布 / false 驳回 → 下架
     */
    @PostMapping("/audit")
    public BaseResponse<String> auditPost(@RequestParam Long postId, @RequestParam Boolean pass,@RequestParam String message) {
        postService.auditPost(postId, pass,message);
        return ResultUtils.success(pass ? "审核通过" : "已驳回");
    }

    /**
     * 管理员查看待审核的稿子（status=审核中，不含草稿/已发布/下架），分页查询。
     *
     * <p>条件与用户列表一致：id / postCode / boardId / title / userId / createdAt
     * （字符串字段模糊匹配、id 类字段精确匹配），均不传则查全部审核中稿子；
     * 未传排序字段时默认按创建时间倒序（最新在前）。</p>
     */
    @PostMapping("/list")
    public BaseResponse<IPage<PostVO>> listReviewingPosts(@RequestBody PostQueryDTO postQueryDTO) {
        return ResultUtils.success(postService.listReviewingPosts(postQueryDTO));
    }



}
