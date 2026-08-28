package com.ruwei.component.notification.listener;

import com.ruwei.component.notification.event.CommentEvent;
import com.ruwei.component.notification.event.LikeEvent;
import com.ruwei.component.notification.event.ShareEvent;
import com.ruwei.component.notification.event.ViewEvent;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.userBehavior;
import com.ruwei.manager.RecCacheManager;
import com.ruwei.service.PostService;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import com.ruwei.service.UserbehaviorService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 推荐兴趣实时监听（@Async("eventTaskExecutor") + AFTER_COMMIT，对齐 ShareEventListener 模式）：
 * 消费 Like/Comment/Share/View 四类行为事件 → 写 userBehavior（离线原料）+ INCR 短期兴趣。
 *
 * <p>信号强度：点赞/评论/分享 = 强信号 1.0；浏览 = 弱信号 0.2（可配 rec.interest.view-delta）。
 * 事件携带不足的维度（Like/Comment/Share 无 post 详情）由本类 getById 一次补齐。</p>
 *
 * <p>去噪约定：LikeEvent 发布方已过滤自赞（取赞不发事件）；ViewEvent 仅登录用户发布（recordView 已过滤游客）；
 * 本监听器不再重复判断。</p>
 */
@Slf4j
@Component
public class RecInterestListener {

    /** 强信号增量（点赞/评论/分享） */
    private static final double STRONG_DELTA = 1.0;
    /** 行为来源：推荐流 */
    private static final int SOURCE_REC = 1;
    /** 兴趣维度常量 */
    private static final int DIM_TAG = 2;
    private static final int DIM_TYPE = 3;
    private static final int DIM_BOARD = 4;
    private static final int DIM_AUTHOR = 5;

    /** 浏览弱信号增量 */
    @Value("${rec.interest.view-delta:0.2}")
    private double viewDelta;

    @Resource
    private PostService postService;
    @Resource
    private UserbehaviorService userbehaviorService;
    @Resource
    private RecCacheManager recCacheManager;


    /** 点赞：action=4，强信号 */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLike(LikeEvent e) {
        Post post = postService.getById(e.getPostId());
        if (post == null) {
            return;
        }
        saveBehavior(e.getActorId(), post, 4);
        incrInterests(e.getActorId(), post, STRONG_DELTA);
    }

    /** 评论（一级评论，ReplyEvent 二级回复口径相同可后续并入）：action=5，强信号 */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onComment(CommentEvent e) {
        Post post = postService.getById(e.getPostId());
        if (post == null) {
            return;
        }
        saveBehavior(e.getCommentUserId(), post, 5);
        incrInterests(e.getCommentUserId(), post, STRONG_DELTA);
    }

    /** 站内分享：action=7，强信号（站外分享暂无事件，见对齐标注） */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onShare(ShareEvent e) {
        Post post = postService.getById(e.getPostId());
        if (post == null) {
            return;
        }
        saveBehavior(e.getActorId(), post, 7);
        incrInterests(e.getActorId(), post, STRONG_DELTA);
    }

    /** 浏览（详情页点击进入）：action=2，弱信号；ViewEvent 已携带 topic/type/boardId，免查 post */
    @Async("eventTaskExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onView(ViewEvent e) {
        if (e.getUserId() == null) {
            return;   // 防御：正常链路 recordView 已过滤游客
        }
        userBehavior ub = new userBehavior();
        ub.setUserId(e.getUserId());
        ub.setPostId(e.getPostId());
        ub.setAction(2);          // 点击进入（详情页打开）
        ub.setSource(SOURCE_REC); // Phase 1 统一记推荐流来源（事件未携带 source，二期埋点位次）
        ub.setPosition(0);
        ub.setDwellSec(0);
        ub.setExtras("");
        try {
            userbehaviorService.save(ub);
        } catch (Exception ex) {
            log.warn("浏览行为落库失败 userId={} postId={}: {}", e.getUserId(), e.getPostId(), ex.getMessage());
        }
        incrInterests(e.getUserId(), e.getTopic(), e.getType(), e.getBoardId(), viewDelta);
    }


    // ---- 私有工具 ----

    /** 写行为日志（行为与兴趣写失败均不影响主流程，只告警）。 */
    private void saveBehavior(Long userId, Post post, int action) {
        userBehavior ub = new userBehavior();
        ub.setUserId(userId);          // 依赖 §2.2 P1 修复
        ub.setPostId(post.getId());
        ub.setAction(action);
        ub.setSource(SOURCE_REC);
        ub.setPosition(0);
        ub.setDwellSec(0);
        ub.setExtras("");
        try {
            userbehaviorService.save(ub);
        } catch (Exception e) {
            log.warn("行为落库失败 userId={} postId={} action={}: {}",
                    userId, post.getId(), action, e.getMessage());
        }
    }

    /** 帖子 → 兴趣维度批量 INCR（dim2 标签 / dim3 类型 / dim4 板块 / dim5 作者）。 */
    private void incrInterests(Long uid, Post post, double delta) {
        incrInterests(uid, post.getTopic(), post.getType(), post.getBoardId(), delta);
        // 作者维度（dim=5）：写兴趣但不参与精排（防大 V 垄断），Phase 2 再启用
        if (post.getUserId() != null) {
            recCacheManager.incrInterest(uid, DIM_AUTHOR, String.valueOf(post.getUserId()), delta);
        }
    }

    /** 维度值直写版（ViewEvent 携带 topic 串 / type / boardId，免查 post）。 */
    private void incrInterests(Long uid, String topic, Integer type, Long boardId, double delta) {
        if (StrUtil.isNotBlank(topic)) {
            for (String tag : StrUtil.split(topic, ',')) {
                String t = StrUtil.trim(tag);
                if (NumberUtil.isLong(t)) {
                    recCacheManager.incrInterest(uid, DIM_TAG, t, delta);
                }
            }
        }
        if (type != null) {
            recCacheManager.incrInterest(uid, DIM_TYPE, String.valueOf(type), delta);
        }
        if (boardId != null) {
            recCacheManager.incrInterest(uid, DIM_BOARD, String.valueOf(boardId), delta);
        }
    }
}