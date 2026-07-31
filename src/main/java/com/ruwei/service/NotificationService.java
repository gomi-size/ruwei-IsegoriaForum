package com.ruwei.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.NotificationQueryDTO;
import com.ruwei.domain.empty.Notification;
import com.ruwei.domain.vo.NotificationVO;

import java.util.List;


/**
* @author Administrator
* @description 针对表【notification(通知表(历史存储/消息中心真相源))】的数据库操作Service
* @createDate 2026-07-30 15:44:44
*/
public interface NotificationService extends IService<Notification> {

    /**
     * 查看登录用户有多少未读信息
     * @return
     */
    Long userUnreadCount();

    /**
     * 分页查询当前登录用户的通知列表（按时间降序）。
     *
     * @param notificationQueryDTO 查询条件（含分页参数 current/pageSize，可选 type 过滤）
     * @return 当前用户的通知 VO 分页（保留 total/current/size）
     */
    IPage<NotificationVO> getAllNotification(NotificationQueryDTO notificationQueryDTO);

    /**
     * 已读
     * @param ids
     */
    void readMessage(List<Long> ids);
}
