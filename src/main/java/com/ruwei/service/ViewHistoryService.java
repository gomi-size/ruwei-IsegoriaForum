package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.empty.ViewHistory;

/**
 * 用户浏览历史服务（去重状态表，upsert 累计）。
 *
 * <p>与 {@code userBehavior} 行为流水职责分离：本服务只维护「浏览历史」这一业务状态
 * （每用户每帖一行，重复浏览累计次数 + 刷新最近浏览时间），供在线查询
 * 「我的浏览历史 / 看过没看过」；行为流水由推荐模块的事件监听器另行写入。</p>
 */
public interface ViewHistoryService extends IService<ViewHistory> {

    /**
     * 记录一次浏览：upsert 累计（首次插入 viewCount=1，再次浏览 +1 并刷新 lastViewAt）。
     *
     * <p>调用方需保证传入登录用户内部 id（游客不调用本方法）；
     * 单条 SQL 原子完成，无并发竞态。</p>
     *
     * @param userId 浏览者内部 id（=loginId）
     * @param postId 帖子内部 id
     */
    void record(Long userId, Long postId);
}
