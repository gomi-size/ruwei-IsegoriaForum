package com.ruwei.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostStatusEnum;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.PostCollect;
import com.ruwei.domain.utils.CountUtils;
import com.ruwei.domain.vo.CollectToggleVO;
import com.ruwei.mapper.PostCollectMapper;
import com.ruwei.service.CollectService;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 帖子收藏服务实现（物理删 toggle，DB 直写）。
 *
 * <p>收藏是低频私密行为，不走点赞的 Redis 先行 + MQ 落库：写路径 insert/delete +
 * {@link CountUtils} 原子计数；读路径一次 IN 查询批量填充「是否已收藏」。
 * 收藏不通知作者（私密行为）。</p>
 */
@Service
public class CollectServiceImpl extends ServiceImpl<PostCollectMapper, PostCollect>
        implements CollectService {

    /** 帖子服务：存在性/状态校验 + collectCount 原子增减（CountUtils） */
    @Resource
    private PostService postService;

    @Override
    public CollectToggleVO toggle(Long postId) {
        Long loginId = StpUtil.getLoginIdAsLong();
        Post post = postService.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");
        // 只有「已发布 + 审核通过」可收藏（对齐点赞口径）
        ThrowUtils.throwIf(!PostStatusEnum.PUBLISHED.matches(post.getStatus())
                        || !PostAuditStatusEnum.APPROVED.matches(post.getAuditStatus()),
                ErrorCode.OPERATION_ERROR, "该帖子当前不可收藏");

        // 查当前是否存在收藏关系（folderId=0 默认收藏夹）
        PostCollect exist = getOne(new LambdaQueryWrapper<PostCollect>()
                .eq(PostCollect::getUserId, loginId)
                .eq(PostCollect::getPostId, postId)
                .eq(PostCollect::getFolderId, 0L));

        if (exist != null) {
            // 取消收藏
            removeById(exist.getId());
            CountUtils.increment(postService, Post::getId, postId, "collectCount", -1);
        } else {
            // 收藏
            PostCollect pc = new PostCollect();
            pc.setUserId(loginId);
            pc.setPostId(postId);
            pc.setFolderId(0L);
            try {
                save(pc);
                CountUtils.increment(postService, Post::getId, postId, "collectCount", 1);
            } catch (DuplicateKeyException e) {
                // 并发兜底：唯一键 ukUserPostFolder 拦住重复收藏，幂等视为已收藏
            }
        }

        Post latest = postService.getById(postId);
        int count = latest.getCollectCount() == null ? 0 : latest.getCollectCount();
        return new CollectToggleVO(exist == null, count);
    }

    @Override
    public Map<Long, Boolean> batchIsCollected(Collection<Long> postIds, Long loginId) {
        if (postIds == null || postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PostCollect> list = lambdaQuery()
                .eq(PostCollect::getUserId, loginId)
                .eq(PostCollect::getFolderId, 0L)
                .in(PostCollect::getPostId, postIds)
                .list();
        Set<Long> collected = list.stream().map(PostCollect::getPostId).collect(Collectors.toSet());
        Map<Long, Boolean> map = new HashMap<>();
        for (Long pid : postIds) {
            map.put(pid, collected.contains(pid));
        }
        return map;
    }

    @Override
    public IPage<PostCollect> pageMyCollect(Long loginId, long current, long pageSize) {
        return lambdaQuery()
                .eq(PostCollect::getUserId, loginId)
                .eq(PostCollect::getFolderId, 0L)
                .orderByDesc(PostCollect::getCreatedAt)
                .page(new Page<>(current, pageSize));
    }
}
