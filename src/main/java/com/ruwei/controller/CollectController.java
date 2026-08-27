package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.PageRequest;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.vo.CollectToggleVO;
import com.ruwei.domain.vo.PostBrowseVO;
import com.ruwei.service.CollectService;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 帖子收藏接口（需登录，物理删 toggle，DB 直写）。
 *
 * <p>收藏为低频私密行为，不走点赞的 Redis 先行 + MQ 落库；收藏不通知作者。
 * {@code folderId} 预留收藏夹（Phase 1 未分组，代码写死 0=默认收藏夹）。</p>
 */
@RestController
@RequestMapping("/collect")
@SaCheckLogin
public class CollectController {

    @Resource
    private CollectService collectService;

    @Resource
    private PostService postService;

    /**
     * 收藏/取消收藏 toggle（无状态翻转），返回 {isCollected, collectCount}。
     */
    @PostMapping("/{postId}")
    public BaseResponse<CollectToggleVO> toggle(@PathVariable Long postId) {
        ThrowUtils.throwIf(postId == null, ErrorCode.PARAMS_ERROR, "帖子id不能为空");
        return ResultUtils.success(collectService.toggle(postId));
    }

    /**
     * 我的收藏列表（按收藏时间倒序分页，返回列表专用 PostBrowseVO）。
     */
    @PostMapping("/list")
    public BaseResponse<IPage<PostBrowseVO>> listMyCollect(@RequestBody PageRequest pageRequest) {
        return ResultUtils.success(postService.listMyCollect(pageRequest.getCurrent(), pageRequest.getPageSize()));
    }

    /**
     * 查询当前用户对某帖子的收藏状态 + 最新收藏数（详情页渲染初始状态）。
     */
    @GetMapping("/{postId}/status")
    public BaseResponse<CollectToggleVO> status(@PathVariable Long postId) {
        ThrowUtils.throwIf(postId == null, ErrorCode.PARAMS_ERROR, "帖子id不能为空");
        Long loginId = StpUtil.getLoginIdAsLong();
        Post post = postService.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        boolean collected = collectService.batchIsCollected(List.of(postId), loginId)
                .getOrDefault(postId, false);
        return ResultUtils.success(new CollectToggleVO(collected, post.getCollectCount()));
    }
}
