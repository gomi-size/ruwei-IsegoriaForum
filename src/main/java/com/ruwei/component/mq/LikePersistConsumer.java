package com.ruwei.component.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.empty.Comment;
import com.ruwei.domain.empty.CommentLike;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.PostLike;
import com.ruwei.domain.utils.CountUtils;
import com.ruwei.es.event.PostIndexEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import com.rabbitmq.client.Channel;
import com.ruwei.domain.dto.LikePersistMessage;
import com.ruwei.mapper.CommentLikeMapper;
import com.ruwei.mapper.PostLikeMapper;
import com.ruwei.service.CommentService;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * 点赞异步落库消费者（「Redis 先行 + MQ 落库」写路径的落库端）。
 *
 * <p>职责：消费 {@code like.exchange} 下发的 {@link LikePersistMessage}，将 Redis 已完成的
 * 「点赞 / 取赞」事件可靠地落库到 {@code post_like} / {@code comment_like}，并回写主表冗余计数列
 * （{@code post.likeCount} / {@code comment.likeCount}）。设计依据
 * {@code docs/modules/11-like-module.md} §7–§9；Redis 为实时展示层，DB 为最终真相源。</p>
 *
 * <h3>可靠性与幂等（四层兜底中的第 3、4 层）</h3>
 * <ul>
 *   <li><b>消费端幂等</b>：每条消息携带 {@code eventId}（UUID），通过 {@link #dedup} 的
 *       {@code SETNX like:mq:dedup:{eventId}}（10 分钟 TTL）去重，重复投递直接 ACK 跳过。</li>
 *   <li><b>DB 唯一键兜底</b>：{@code ukPostUser} / {@code ukCommentUser} 保证关系不重复；
 *       重复 INSERT 命中唯一键按幂等成功处理。</li>
 *   <li><b>计数防漂移</b>：计数增减与关系变更严格绑定——仅当 INSERT 影响行数=1 才 {@code +1}，
 *       仅当 DELETE 影响行数=1 才 {@code -1}（见 {@link CountUtils#increment} 的 {@code setSql} 原子增减），
 *       杜绝「关系幂等跳过却仍 +1」导致的计数漂移（对齐 §8.2）。</li>
 *   <li><b>手动 ACK + 死信</b>：{@code acknowledge-mode=manual}，处理成功才 ACK；异常 {@code Nack(requeue=false)}
 *       进死信队列（{@code like.post.dlq} / {@code like.comment.dlq}）告警人工介入。</li>
 * </ul>
 *
 * <h3>并发</h3>
 * 两个监听方法均 {@code concurrency = "4"}；同一 {@code postId}/{@code commentId} 的消息不要求顺序，
 * 最终状态由 Redis（用户当前意图）决定，DB 只存最终关系（对齐 §7.4）。
 *
 * @see LikePersistMessage
 * @see com.ruwei.config.LikeMqConfig
 * @see com.ruwei.manager.LikeCacheManager
 */
@Component
@Slf4j
public class LikePersistConsumer {

    /** MQ 消费去重标记 Redis Key 前缀：{@code like:mq:dedup:{eventId}} */
    private static final String DEDUP_PREFIX = "like:mq:dedup:";
    /** 去重标记过期时间：10 分钟（对齐 docs/modules/11-like-module.md §6.1） */
    private static final Duration DEDUP_TTL = Duration.ofMinutes(10);

    @Resource
    private PostLikeMapper postLikeMapper;       // 帖子点赞表 DAO
    @Resource
    private CommentLikeMapper commentLikeMapper; // 评论点赞表 DAO
    @Resource
    private PostService postService;             // 帖子主表服务（用于更新总数）
    @Resource
    private CommentService commentService;       // 评论主表服务（用于更新总数）
    @Resource
    private StringRedisTemplate redis;
    @Resource
    private ApplicationEventPublisher eventPublisher;


    /**
     * 帖子点赞事件消费者。
     *
     * <p>流程（手动 ACK）：① {@link #dedup} 去重，已处理过的 {@code eventId} 直接 ACK 跳过；
     * ② {@link #persistPost} 落库 + 回写计数；③ 成功则 ACK；④ 任何异常则
     * {@code Nack(requeue=false)} 进 {@code like.post.dlq}（本地重试耗尽后由 broker 投递死信）。</p>
     *
     * @param msg     点赞落库消息（{@code targetType=1} 帖子）
     * @param message AMQP 消息（取 deliveryTag 用于手动 ACK/NACK）
     * @param channel 信道（手动 ACK 所需）
     * @throws IOException 信道操作异常时向上抛出，交由框架/重试机制处理
     */
    @RabbitListener(queues = "like.post.queue", concurrency = "4")
    public void onPostMessage(LikePersistMessage msg, Message message , Channel channel) throws IOException {
        long tag = message.getMessageProperties().getDeliveryTag();
        try{
            // 幂等去重：已处理过（eventId 命中）则直接 ACK 跳过
            if(!dedup(msg.getEventId())){
                // 创建的是一个tcp通道
                channel.basicAck(tag,false);
                return;
            }
            // 点赞落库与防漂移逻辑（核心业务区）
            persistPost(msg);
            // 处理完后删除掉这个消息
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("评论点赞落库失败 eventId={} targetId={}", msg.getEventId(), msg.getTargetId(), e);
            channel.basicNack(tag, false, false);
        }

    }


    /**
     * 评论点赞事件消费者，语义同 {@link #onPostMessage}（监听 {@code like.comment.queue}，
     * 异常进 {@code like.comment.dlq}）。
     *
     * @param msg     点赞落库消息（{@code targetType=2} 评论）
     * @param message AMQP 消息
     * @param channel 信道（手动 ACK 所需）
     * @throws Exception 信道操作异常时向上抛出
     */
    @RabbitListener(queues = "like.comment.queue", concurrency = "4")
    public void onCommentMessage(LikePersistMessage msg, Message message,
                                 com.rabbitmq.client.Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            if (!dedup(msg.getEventId())) {
                channel.basicAck(tag, false);
                return;
            }
            persistComment(msg);
            channel.basicAck(tag, false);
        } catch (Exception e) {
            log.error("评论点赞落库失败 eventId={} targetId={}", msg.getEventId(), msg.getTargetId(), e);
            channel.basicNack(tag, false, false);
        }
    }


    /**
     * 基于 Redis 的 MQ 消费幂等去重（SETNX）。
     *
     * <p>对 {@code like:mq:dedup:{eventId}} 执行 {@code SETNX} 并设置 10 分钟 TTL：
     * <ul>
     *   <li>返回 {@code true}：本次是首次见到该 {@code eventId}，SETNX 成功，应当继续处理；</li>
     *   <li>返回 {@code false}：该 {@code eventId} 已被处理过（键已存在），应直接 ACK 跳过。</li>
     * </ul>
     * TTL 确保极端延迟（如死信重放超过 10 分钟）后键过期，即使重复也能靠 DB 唯一键再次兜底。</p>
     *
     * @param eventId 消息幂等键（UUID，由生产端 {@code LikeService} 生成）
     * @return {@code true}=首次处理（SETNX 成功），{@code false}=已处理过（跳过）
     */
    private boolean dedup(String eventId){
        Boolean ok = redis.opsForValue().setIfAbsent(DEDUP_PREFIX + eventId, "1", DEDUP_TTL);

        return Boolean.TRUE.equals(ok);
    }


    /**
     * 帖子点赞落库 + 主表计数回写（防漂移）。
     *
     * <p>action=1（点赞）：INSERT {@code post_like}；仅当真正新增（影响行数=1）才
     * {@code post.likeCount +1}（命中唯一键 {@code ukPostUser} 视为幂等成功，不重复计数）。</p>
     * <p>action=0（取赞）：DELETE {@code post_like}（按 postId+userId）；仅当确实删除了记录
     * （影响行数=1）才 {@code post.likeCount -1}，避免「无记录却 -1」导致计数下穿。</p>
     * <p><b>ES 索引同步</b>：计数真实变更后发布 {@link PostIndexEvent}（INDEX），由
     * {@code PostIndexEventListener} 异步重建 ES 文档（{@code indexByPostId} 内部带 shouldIndex 兜底），
     * 保证主页推荐流（{@code /search/post} 读 ES 索引）的 likeCount 与 DB 一致；
     * 幂等跳过（计数未变）时不发布，避免无谓重建。</p>
     *
     * @param msg 点赞落库消息（{@code targetType=1}，{@code targetId}=postId）
     */
    private void persistPost(LikePersistMessage msg) {
        Long postId = msg.getTargetId();
        Long userId = msg.getUserId();
        if (msg.getAction() == 1) {
            PostLike record = new PostLike();
            record.setPostId(postId);
            record.setUserId(userId);
            record.setCreatedAt(new java.util.Date());
            try {
                boolean inserted = postLikeMapper.insert(record) > 0;
                if (inserted) {                         // 仅真正新增才 +1，防漂移
                    CountUtils.increment(postService, Post::getId, postId, "likeCount", 1);
                    // 点赞数真实变更 → 重建 ES 索引，保证推荐流计数一致
                    eventPublisher.publishEvent(new PostIndexEvent(this, postId, PostIndexEvent.Action.INDEX));
                }
            } catch (DuplicateKeyException ex) {
                log.warn("点赞幂等跳过（唯一键命中）postId={} userId={}", postId, userId);
            }
        } else {
            int removed = postLikeMapper.delete(new LambdaQueryWrapper<PostLike>()
                    .eq(PostLike::getPostId, postId).eq(PostLike::getUserId, userId));
            if (removed > 0) {
                CountUtils.increment(postService, Post::getId, postId, "likeCount", -1);
                // 取赞同样真实变更 → 重建 ES 索引
                eventPublisher.publishEvent(new PostIndexEvent(this, postId, PostIndexEvent.Action.INDEX));
            }
        }
    }

    /**
     * 评论点赞落库 + 主表计数回写（防漂移），语义同 {@link #persistPost}
     * （{@code targetType=2}，{@code targetId}=commentId，操作 {@code comment_like} / {@code comment.likeCount}）。
     *
     * @param msg 点赞落库消息（{@code targetType=2}，{@code targetId}=commentId）
     */
    private void persistComment(LikePersistMessage msg) {
        Long commentId = msg.getTargetId();
        Long userId = msg.getUserId();
        if (msg.getAction() == 1) {
            CommentLike record = new CommentLike();
            record.setCommentId(commentId);
            record.setUserId(userId);
            record.setCreatedAt(new java.util.Date());
            try {
                boolean inserted = commentLikeMapper.insert(record) > 0;
                if (inserted) {
                    CountUtils.increment(commentService, Comment::getId, commentId, "likeCount", 1);
                }
            } catch (Exception ex) {
                log.warn("评论点赞幂等跳过（唯一键命中）commentId={} userId={}", commentId, userId);
            }
        } else {
            int removed = commentLikeMapper.delete(new LambdaQueryWrapper<CommentLike>()
                    .eq(CommentLike::getCommentId, commentId).eq(CommentLike::getUserId, userId));
            if (removed > 0) {
                CountUtils.increment(commentService, Comment::getId, commentId, "likeCount", -1);
            }
        }
    }
}
