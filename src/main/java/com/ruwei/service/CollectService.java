package com.ruwei.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.empty.PostCollect;
import com.ruwei.domain.vo.CollectToggleVO;

import java.util.Collection;
import java.util.Map;

/**
 * 帖子收藏服务（物理删 toggle，DB 直写）。
 *
 * <p>收藏是<b>低频私密行为</b>，不走点赞那套 Redis 先行 + MQ 落库（过度设计）：
 * 写路径 DB 直写（insert/delete）+ {@code CountUtils} 原子计数；
 * 读路径一次 IN 查询批量填充「是否已收藏」。收藏<b>不通知作者</b>（type=7 预留暂不启用）。</p>
 */
public interface CollectService extends IService<PostCollect> {

    /**
     * 收藏/取消收藏 toggle（无状态翻转）。
     *
     * <p>流程：帖子存在 + 已发布 + 审核通过校验（对齐点赞口径）→ 查是否存在收藏关系
     * （userId + postId + folderId=0）→ 有则删（collectCount-1）、无则插（collectCount+1，
     * 并发下撞唯一键 {@code ukUserPostFolder} 由 {@code DuplicateKeyException} 兜底幂等）。</p>
     *
     * @param postId 帖子内部 id
     * @return 切换后状态 {isCollected, collectCount}
     */
    CollectToggleVO toggle(Long postId);

    /**
     * 批量填充「是否已收藏」（供列表装配 fillIsCollected 调用，一次 IN 查，无 N+1）。
     *
     * @param postIds 帖子内部 id 集合
     * @param loginId 当前用户内部 id
     * @return postId → 是否已收藏（缺失默认 false）
     */
    Map<Long, Boolean> batchIsCollected(Collection<Long> postIds, Long loginId);

    /**
     * 分页查询我的收藏关系（按收藏时间倒序），供 PostService.listMyCollect 装配。
     *
     * @param loginId  当前用户内部 id
     * @param current  页码（从 1 开始）
     * @param pageSize 每页条数
     * @return 收藏关系分页结果（PostCollect，folderId=0 默认收藏夹）
     */
    IPage<PostCollect> pageMyCollect(Long loginId, long current, long pageSize);
}
