package com.ruwei.component.notification.Publisher;

import com.ruwei.domain.dto.NotifyPushMessage;

/** 通知发布抽象：把通知实时推送给指定内部 id 的用户 */
public interface NotificationPublisher {
    /**
     * 推送一条通知给内部 id 对应的用户。
     * 失败静默（用户离线/WS 未连），历史以 notification 表为准，前端上线后自行拉取。
     */
    void push(Long internalId, NotifyPushMessage message);
}