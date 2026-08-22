package com.ruwei.schedule;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruwei.domain.empty.PostLike;
import com.ruwei.manager.LikeCacheManager;
import com.ruwei.mapper.PostLikeMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 点赞计数对账：每 5 分钟校准 Redis 计数与 DB 行数（11 §11.2）。
 * 以 post_like 真实行数为准；dirty 集合驱动，空跑成本≈0。
 */
@Slf4j
@Component
public class LikeReconcileJob {

    @Resource
    private LikeCacheManager likeCacheManager;
    @Resource
    private PostLikeMapper postLikeMapper;

    @Scheduled(cron = "0 */5 * * * *")
    public void reconcile() {
        Set<Long> dirty = likeCacheManager.takeDirtyPosts();
        if(dirty.isEmpty()){
            return ;
        }
        int fixed=0;
        for ( Long postId:dirty){
            Long dbReal = postLikeMapper.selectCount(new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId));
            long redisReal = likeCacheManager.getPostLikeCount(postId);
            //如果两边数据不相等那就产生脏数据了
            if(redisReal!=dbReal){
                likeCacheManager.setPostCount(postId, dbReal);
                fixed++;
                log.info("点赞对账校正 postId={} redis={} dbReal={}", postId, redisReal, dbReal);
            }

        }
        if (fixed > 0) {
            log.warn("点赞对账本批校正 {} 条（阈值告警 100）", fixed);
        }
    }
}
