package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.dto.NotificationQueryDTO;
import com.ruwei.domain.vo.NotificationVO;
import com.ruwei.service.NotificationService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@SaCheckLogin
public class NotificationController {

    @Resource
    private NotificationService  notificationService;

    /**
     * 查看登录用户有多少未读（红点）
     * 当用户成功后系统自动调用
     */
    @GetMapping("/unReadCount")
    public BaseResponse<Long> userUnreadCount(){
       Long count= notificationService.userUnreadCount();
       return ResultUtils.success(count);
    }

    /**
     * 查看所有信息
     */
    @PostMapping("/getAllNf")
    public BaseResponse<IPage<NotificationVO>> getAllNotification(@RequestBody NotificationQueryDTO notificationQueryDTO){
        return ResultUtils.success(notificationService.getAllNotification(notificationQueryDTO));
    }

    /**
     * 标记已读，若不传递信息id则全部已读。
     * 入参为表单/url 参数（非 JSON）：ids=1&ids=2&ids=3；不传则全部已读。
     */
    @PostMapping("/read")
    public BaseResponse<String> readMessage(@RequestParam(required = false) List<Long> ids){

        notificationService.readMessage(ids);

        return ResultUtils.success("已读成功");
    }
}