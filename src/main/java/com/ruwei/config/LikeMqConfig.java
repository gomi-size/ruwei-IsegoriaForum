package com.ruwei.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 点赞模块 MQ 拓扑：
 *   like.exchange (direct, durable)
 *     ├─ rk like.post     → like.post.queue     (durable, dlq=like.post.dlq)
 *     └─ rk like.comment  → like.comment.queue  (durable, dlq=like.comment.dlq)
 *   like.dlx (direct, durable) 承载死信转发
 * 消费者手动 ACK；本地重试 3 次耗尽后 nack(requeue=false) → 进死信队列，告警人工处理。
 */
@Configuration
public class LikeMqConfig {

    //1.定义交换机
    public static final String EXCHANGE = "like.exchange";
    //死信交换机
    public static final String DLX = "like.dlx";

    public static final String POST_QUEUE = "like.post.queue";
    public static final String POST_DLQ = "like.post.dlq";
    public static final String POST_RK = "like.post";

    public static final String COMMENT_QUEUE = "like.comment.queue";
    public static final String COMMENT_DLQ = "like.comment.dlq";
    public static final String COMMENT_RK = "like.comment";

    //直连交换机
    @Bean
    public DirectExchange likeExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    //死信交换机
    @Bean
    public DirectExchange likeDlx() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    //构建带死信功能的队列（专门用来创建“业务队列”并给它们绑定死信规则）
    private Queue buildQueueWithDlq(String queue, String dlq) {
        return QueueBuilder.durable(queue)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", dlq)
                .build();
    }

    //声明队列
    @Bean
    public Queue likePostQueue() {
        return buildQueueWithDlq(POST_QUEUE, POST_DLQ);
    }

    @Bean
    public Queue likeCommentQueue() {
        return buildQueueWithDlq(COMMENT_QUEUE, COMMENT_DLQ);
    }

    @Bean
    public Queue likePostDlq() {
        return QueueBuilder.durable(POST_DLQ).build();
    }

    @Bean
    public Queue likeCommentDlq() {
        return QueueBuilder.durable(COMMENT_DLQ).build();
    }

    @Bean
    public Binding likePostBinding() {
        return BindingBuilder.bind(likePostQueue()).to(likeExchange()).with(POST_RK);
    }

    @Bean
    public Binding likeCommentBinding() {
        return BindingBuilder.bind(likeCommentQueue()).to(likeExchange()).with(COMMENT_RK);
    }

    @Bean
    public Binding likePostDlqBinding() {
        return BindingBuilder.bind(likePostDlq()).to(likeDlx()).with(POST_DLQ);
    }

    @Bean
    public Binding likeCommentDlqBinding() {
        return BindingBuilder.bind(likeCommentDlq()).to(likeDlx()).with(COMMENT_DLQ);
    }
}