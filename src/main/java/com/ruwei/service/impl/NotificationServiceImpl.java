package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.NotificationQueryDTO;
import com.ruwei.domain.empty.Notification;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.vo.NotificationVO;
import com.ruwei.domain.utils.QueryWrapperUtils;
import com.ruwei.domain.vo.SenderVO;
import com.ruwei.mapper.NotificationMapper;
import com.ruwei.service.NotificationService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
* @author Administrator
* @description 针对表【notification(通知表(历史存储/消息中心真相源))】的数据库操作Service实现
* @createDate 2026-07-30 15:44:44
*/
@Service
public class NotificationServiceImpl extends ServiceImpl<NotificationMapper, Notification>
    implements NotificationService{

    @Resource
    private UserService userService;

    /**
     * 查看用户有多少未读信息
     * @return
     */
    @Override
    public Long userUnreadCount() {
        long loginId = StpUtil.getLoginIdAsLong();
        return lambdaQuery().eq(Notification::getReceiverId, loginId)
                .eq(Notification::getIsRead, 0)
                .count();
    }

    /**
     * 分页查询当前登录用户的通知列表（按时间降序）。
     */
    @Override
    public IPage<NotificationVO> getAllNotification(NotificationQueryDTO notificationQueryDTO) {
        long loginId = StpUtil.getLoginIdAsLong();
        QueryWrapper<Notification> queryWrapper =
                QueryWrapperUtils.getNotificationQueryWrapper(loginId, notificationQueryDTO);
        //2081585972414304257
        Page<Notification> page = this.page(
                new Page<>(notificationQueryDTO.getCurrent(), notificationQueryDTO.getPageSize()),
                queryWrapper);

        // 1) 收集本页所有 senderId，批量查 User，避免逐条 N+1
        List<Long> senderIds = page.getRecords().stream()
                .map(Notification::getSenderId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, SenderVO> senderMap;
        if (!senderIds.isEmpty()) {
            senderMap = userService.listByIds(senderIds).stream()
                    .collect(Collectors.toMap(
                            User::getId,
                            u -> {
                                SenderVO s = new SenderVO();
                                s.setId(u.getId());
                                s.setUserId(u.getUserId());
                                s.setNickname(u.getNickname());
                                s.setAvatar(u.getAvatar());
                                s.setLevel(u.getLevel());
                                return s;
                            },
                            (a, b) -> a));
        } else {
            senderMap = Collections.emptyMap();
        }
        if(senderMap.isEmpty()){
            return new Page<>(notificationQueryDTO.getCurrent(), notificationQueryDTO.getPageSize());
        }

        // 2) 转 VO 并把发送者信息填进去；convert 会保留 total/current/size 分页元数据
        return page.convert(notification -> {
            NotificationVO vo = BeanUtil.copyProperties(notification, NotificationVO.class);
            vo.setSender(senderMap.get(notification.getSenderId()));
            return vo;
        });
    }

    /**
     * 已读
     * @param ids
     */
    @Override
    public void readMessage(List<Long> ids) {
        long loginId = StpUtil.getLoginIdAsLong();
        LambdaUpdateWrapper<Notification> lambdaUpdateWrapper =new LambdaUpdateWrapper<>();
        lambdaUpdateWrapper.eq(Notification::getReceiverId,loginId)
                .eq(Notification::getIsRead,0);
        // ids 为 null（不传）或空列表时，不追加 in 条件 → 标记该用户全部未读为已读
        if (ids != null && !ids.isEmpty()){
            lambdaUpdateWrapper.in(Notification::getId,ids);
        }
        lambdaUpdateWrapper.set(Notification::getIsRead,1);
        boolean update = update(lambdaUpdateWrapper);

        ThrowUtils.throwIf(!update,ErrorCode.OPERATION_ERROR,"更新失败");
    }
}




