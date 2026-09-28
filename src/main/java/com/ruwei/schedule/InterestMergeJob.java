package com.ruwei.schedule;


import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.unit.DataUnit;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruwei.domain.empty.UserInterest;
import com.ruwei.domain.empty.userBehavior;
import com.ruwei.manager.RecCacheManager;
import com.ruwei.service.UserInterestService;
import com.ruwei.service.UserbehaviorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期兴趣合并 Job（每日 03:30，错开 EsReconcileTask 的 03:00 避免 DB 同时压力）：
 * 最近 3 天有行为的用户 → 短期兴趣（Redis，TTL 3 天）按指数衰减合并进 user_interest → 清理短期键。
 *
 * <p>合并公式：weight = weight×0.9 + 短期增量；新维度直接以增量起步。
 * upsert 依据唯一键 ukUserDimVal(userId,dimension,value)——Job 单线程串行写入，
 * 无并发冲突；saveOrUpdateBatch 按 id 判空走 insert/update。</p>
 */
@Component
@Slf4j
public class InterestMergeJob {

    /** 旧权重衰减系数（每日合并衰减 10%） */
    private static final double DECAY = 0.9;
    /** 行为回看窗口：与短期兴趣 TTL 3 天对齐 */
    private static final int LOOKBACK_DAYS = 3;

    @Resource
    private UserbehaviorService userbehaviorService;
    @Resource
    private UserInterestService userInterestService;
    @Resource
    private RecCacheManager recCacheManager;

    /** 画像遗忘阈值（天）：超过该天数未被任何行为强化的维度整行删除 */
    private static final int STALE_DAYS = 15;
    /** 全局清理单批条数（分批防单次大事务） */
    private static final int CLEAN_BATCH_SIZE = 5000;

    /**
     * 每日 03:30 执行（EsReconcileTask 为 03:00，错峰半小时）。
     */
    @Scheduled(cron = "0 30 3 * * ?")
    public void merge() {
        log.info("========== 长期兴趣合并开始 ==========");
        try {
            doMerge();
        } catch (Exception e) {
            log.error("长期兴趣合并异常", e);
        }
        log.info("========== 长期兴趣合并结束 ==========");
    }

    private void doMerge() {

        Date since = DateUtil.offsetDay(new Date(), -LOOKBACK_DAYS);
        //直接获取所有的用户
        List<Long> userIds = userbehaviorService.lambdaQuery()
                .gt(userBehavior::getCreatedAt, since)
                .select(userBehavior::getUserId)
                .list().stream()
                .map(userBehavior::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if(userIds.isEmpty()){
            log.info("长期兴趣合并：最近 {} 天无行为用户，跳过", LOOKBACK_DAYS);
        }else{
            int merged = 0;
            for (Long uid : userIds) {
                try {
                    merged += mergeOne(uid);
                } catch (Exception e) {
                    // 单用户失败不影响其余（幂等：短期键未删，明日重跑自然重试）
                    log.warn("兴趣合并失败 uid={}: {}", uid, e.getMessage());
                }
            }
            log.info("长期兴趣合并完成：{} 个用户，{} 条画像", userIds.size(), merged);

        }
        // 全局兜底清理：死户（近期无行为、不在上面 mergeOne 范围内）的超期画像同样要遗忘。
        // 条件走 idxLastActiveAt 索引；分批 LIMIT 防单次大事务（Job 单线程串行，无并发冲突）。
        int cleanedTotal = 0;
        int batch;
        do {
            batch = userInterestService.getBaseMapper().delete(
                    new LambdaQueryWrapper<UserInterest>()
                            .lt(UserInterest::getLastActiveAt, DateUtil.offsetDay(new Date(), -STALE_DAYS))
                            .last("LIMIT " + CLEAN_BATCH_SIZE));
            cleanedTotal += batch;
        } while (batch >= CLEAN_BATCH_SIZE);
        log.info("长期兴趣合并：全局清理超期画像 {} 条", cleanedTotal);
    }
    /**
     * 单用户合并：短期兴趣 → 长期画像（upsert）→ 删短期键。
     *
     * @return 本用户合并的画像条数
     */
    private int mergeOne(Long userId){
        Map<String, Double> shortTerm = recCacheManager.scanShortInterest(userId);
        if(CollUtil.isEmpty(shortTerm)){
            return 0;
        }
        // 3. 查现有画像，按 "dim:value" 建索引（一次查全，避免逐条 upsert N 次查询）
        Map<String, UserInterest> existing = userInterestService.lambdaQuery()
                .eq(UserInterest::getUserId, userId)
                .list().stream()
                .collect(Collectors.toMap(
                        i -> i.getDimension() + ":" + i.getValue(), i -> i, (a, b) -> a));

        Date now=new Date();
        List<UserInterest> toSave = new ArrayList<>();

        for(Map.Entry<String,Double> en:shortTerm.entrySet()){
            String key = en.getKey(); //取出来是 “dim:value”
            Double delta = en.getValue();
            int idx=key.indexOf(':');
            if(idx<=0){
                //key污染
                continue;
            }
            int dim;
            try{
                dim=Integer.parseInt(key.substring(0,idx));
            }catch (NumberFormatException e){
                continue;
            }
            String value = key.substring(idx + 1);

            UserInterest cur = existing.get(key);
            if (cur == null) {
                // 新维度：以短期增量起步
                cur = new UserInterest();
                cur.setUserId(userId);
                cur.setDimension(dim);
                cur.setValue(value);
                cur.setWeight(BigDecimal.valueOf(delta).setScale(4, RoundingMode.HALF_UP));
                cur.setLastActiveAt(now);
            } else {
                // 已有维度：weight = weight×0.9 + 短期增量（指数衰减）
                double oldW = cur.getWeight() == null ? 0d : cur.getWeight().doubleValue();
                cur.setWeight(BigDecimal.valueOf(oldW * DECAY + delta).setScale(4, RoundingMode.HALF_UP));
                cur.setLastActiveAt(now);
            }
            toSave.add(cur);
        }
        // 4. 批量 upsert（单线程 Job 无并发，saveOrUpdateBatch 按 id 判空 insert/update，
        //    唯一键 ukUserDimVal 兜底幂等）+ 删短期键（防残留，TTL 3 天本来也会过期）
        if (!toSave.isEmpty()) {
            userInterestService.saveOrUpdateBatch(toSave);
        }
        recCacheManager.deleteShortInterest(userId);
        return toSave.size();
    }
}

