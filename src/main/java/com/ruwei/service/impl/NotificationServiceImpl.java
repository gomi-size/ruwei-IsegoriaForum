package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.Notification;
import com.ruwei.service.NotificationService;
import com.ruwei.mapper.NotificationMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【notification(通知表(历史存储/消息中心真相源))】的数据库操作Service实现
* @createDate 2026-07-30 15:44:44
*/
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
    implements NotificationService{

}




