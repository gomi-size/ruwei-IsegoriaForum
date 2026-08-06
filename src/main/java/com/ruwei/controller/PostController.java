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
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruwei.domain.dto.PostQueryDTO;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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

    /**
     * 查看草稿箱（当前登录用户 status=草稿 的帖子列表，按创建时间倒序）。
     *
     * <p>作者取当前登录态（不信任前端传 userId，防查他人草稿）；
     * 草稿即 status=草稿（code=2）的记录——「编辑传 status=草稿」时后端复制原帖另存，
     * 不走审核、不对外展示。审核中（code=3）的帖子属于送审流程，不算草稿。</p>
     */
    @GetMapping("/drafts")
    public BaseResponse<List<PostVO>> getDraftList() {
        return ResultUtils.success(postService.getDraftList());
    }

    /**
     * （点进主页后的查询 / 帖子列表页）分页查询帖子：可查自己的稿子、也可查别人的稿子。
     *
     * <p>条件：id / postCode / boardId / title / userId / createdAt（字符串字段模糊匹配、id 类字段精确匹配），
     * 均不传则查询全部；未传排序字段时默认按创建时间倒序（最新在前）。</p>
     *
     * <p><b>可见性</b>：仅当 userId 条件等于当前登录用户（查自己）时放行全部状态（草稿/审核中/下架可见）；
     * 查询全部或他人帖子时只返回「已发布」，草稿/审核中/下架不可见。</p>
     */
    @PostMapping("/list")
    public BaseResponse<IPage<PostVO>> listPosts(@RequestBody PostQueryDTO postQueryDTO) {
        return ResultUtils.success(postService.listPosts(postQueryDTO));
    }

}
