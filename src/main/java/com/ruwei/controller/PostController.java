package com.ruwei.controller;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.PostDTO;
import com.ruwei.domain.vo.PostVO;
import com.ruwei.service.PostService;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 帖子（内容）相关接口
 *
 * <p>审核约定：创建送审（status=3+auditStatus=1）；编辑<b>先审后发</b>（新内容直接覆盖正式字段并重新置为
 * 审核中，审核期间不对外展示，通过后恢复已发布、驳回则下架）；删除为逻辑删除（isDelete=1）。</p>
 *
 * <p><b>两个状态维度的分工</b>：{@code status}（生命周期：已发布/草稿/审核中/下架）由上述流程单向推进，
 * 作者无法手动指定；{@code visibility}（可见性：公开/仅粉丝可见/私密）才是作者可自由设置的维度。</p>
 */
@RestController
@RequestMapping("/post")
@SaCheckLogin
public class PostController {

    @Resource
    private PostService postService;

    /**
     * 创建帖子（送审：创建后 status=3 审核中 + auditStatus=1 待审，管理员审核通过后对外可见）
     *
     * <p>返回 {@link PostVO}：visibility / status / auditStatus 回显<b>中文文字</b>
     * （与入参同一套词汇），id / userId / boardId 序列化为字符串（雪花 id 防前端丢精度）。</p>
     */
    @PostMapping("/add")
    public BaseResponse<PostVO> createPost(@RequestBody PostDTO postDTO) {
        PostVO postVO = postService.createPost(postDTO);
        return ResultUtils.success(postVO);
    }

    /**
     * 编辑帖子（先审后发：草稿另存为新记录直接生效；其余直接覆盖正式字段并重新送审，审核期间不对外展示）
     */
    @PostMapping("/update")
    public BaseResponse<String> updatePost(@RequestBody PostDTO postDTO) {

        return postService.updatePost(postDTO);
    }

    /**
     * 设置帖子可见性（作者本人操作）
     *
     * <p>visibility：前端传<b>文字</b>（"公开" / "仅粉丝可见" / "私密"），
     * 后端经 {@code PostVisibilityEnum} 转整数（1/2/3）落库。</p>
     *
     * <p>只改可见范围，<b>不影响生命周期 status 与审核结果 auditStatus</b> ——
     * 后两者由创建送审、编辑送审、管理员审核三条流程单向推进，不开放给作者手动指定。</p>
     */
    @PostMapping("/visibility")
    public BaseResponse<String> updatePostVisibility(@RequestParam Long id, @RequestParam String visibility) {
        ThrowUtils.throwIf(id == null || StrUtil.isBlank(visibility), ErrorCode.PARAMS_ERROR, "参数不能为空");
        postService.updatePostVisibility(id, visibility);
        return ResultUtils.success("设置可见性成功");
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
