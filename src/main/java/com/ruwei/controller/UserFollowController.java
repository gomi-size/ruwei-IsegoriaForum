package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.UserFollowService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关注关系的控制类
 */
@SaCheckLogin
@RestController
@RequestMapping("/userFollow")
public class UserFollowController {
    @Resource
    private UserFollowService userFollowService;

    /**
     * 关注
     */

    @PostMapping("/follow")
    public BaseResponse<String> followUser(Long userId){
        userFollowService.followUser(userId);
        return ResultUtils.success("关注成功");
    }

    /**
     * 取消关注
     */
    @PostMapping("/cancelFollow")
    public BaseResponse<String> cancelFollowUser(Long userId){
        userFollowService.cancelFollowUser(userId);
        return ResultUtils.success("取消成功");
    }
    /**
     * 查看关注列表
     */
    @GetMapping("/getFollowUserList")
    public BaseResponse<List<UserVO>> getFollowUserList(){
       List<UserVO>  userVOList=userFollowService.getFollowUserList();
       return ResultUtils.success(userVOList);
    }
    /**
     * 查看粉丝列表
     */
    @GetMapping("/getFansUserList")
    public BaseResponse<List<UserVO>> getFansUserList(){
        List<UserVO>  userVOList=userFollowService.getFansUserList();
        return ResultUtils.success(userVOList);
    }
}
