package com.ruwei.controller;


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
@RequestMapping("/post")
@SaCheckLogin
public class PostController {

    @Resource
    private PostService postService;

    /**
     * 创建帖子（送审：创建后 status=3 审核中 + auditStatus=1 待审，管理员审核通过后对外可见）
     */
    @PostMapping("/add")
    public BaseResponse<Post> createPost(@RequestBody PostDTO postDTO) {
        Post post = postService.createPost(postDTO);
        return ResultUtils.success(post);
    }

    /**
     * 编辑帖子（先发后审：草稿直接生效；已发布/下架的内容编辑后进入审核，旧内容继续展示）
     */
    @PostMapping("/update")
    public BaseResponse<String> updatePost(@RequestBody PostDTO postDTO) {
        postService.updatePost(postDTO);
        return ResultUtils.success("编辑成功，已提交审核");
    }

    /**
     * 设置帖子状态（不走审核，作者本人操作）
     * status：1已发布 / 2草稿 / 4下架
     */
    @PostMapping("/status")
    public BaseResponse<String> updatePostStatus(@RequestParam Long id, @RequestParam Integer status) {
        ThrowUtils.throwIf(id == null || status == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        postService.updatePostStatus(id, status);
        return ResultUtils.success("设置状态成功");
    }

    /**
     * 删除帖子（逻辑删除 isDelete=1，作者或管理员）
     */
    @DeleteMapping("/{id}")
    public BaseResponse<String> deletePost(@PathVariable Long id) {
        postService.deletePost(id);
        return ResultUtils.success("删除成功");
    }

}
