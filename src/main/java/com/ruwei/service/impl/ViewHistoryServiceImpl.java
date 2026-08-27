package com.ruwei.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.empty.ViewHistory;
import com.ruwei.mapper.ViewHistoryMapper;
import com.ruwei.service.ViewHistoryService;
import org.springframework.stereotype.Service;

/**
 * 用户浏览历史服务实现。
 *
 * <p>浏览历史是<b>去重状态表</b>：首次浏览插入、再次浏览累计，靠
 * {@link ViewHistoryMapper#upsertView} 一条 SQL 原子完成，无并发竞态
 * （对齐项目「计数走 DB 层原子」的约定，但这里行可能不存在，故不用 CountUtils 两步式）。</p>
 */
@Service
public class ViewHistoryServiceImpl extends ServiceImpl<ViewHistoryMapper, ViewHistory>
        implements ViewHistoryService {

    @Override
    public void record(Long userId, Long postId) {
        ThrowUtils.throwIf(userId == null || postId == null, ErrorCode.PARAMS_ERROR, "参数不能为空");
        baseMapper.upsertView(IdWorker.getId(), userId, postId);
    }
}
