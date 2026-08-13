package com.ruwei.component.notification.listener;


import com.ruwei.component.notification.event.FollowEvent;
import com.ruwei.manager.FollowCacheManager;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class FollowRedisEventListener {

    @Resource
    private FollowCacheManager cache;


    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onFollowCache(FollowEvent event){
        try {
            if(event.getAction()==FollowEvent.ACTION_FOLLOW){
                //添加关注
                cache.addFollowing(event.getActorId(), event.getFolloweeId());
            }
            else {
                cache.removeFollow(event.getActorId(), event.getFolloweeId());
            }
        } catch (Exception e) {
            // 事务已提交，不能回滚 DB；记日志告警，键过期后读路径会回源重建自愈
            log.error("同步关注关系到 Redis 失败: actor={}, followee={}, action={}",
                    event.getActorId(), event.getFolloweeId(), event.getAction(), e);
        }
    }
}
