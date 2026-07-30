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
     * 入参可传 id（内部主键）或 userId（对外编码），二选一即可，库内统一存内部 id
     */
    @PostMapping("/follow")
    public BaseResponse<String> followUser(Long id, Long userId){
        userFollowService.followUser(id, userId);
        return ResultUtils.success("关注成功");
    }

    /**
     * 取消关注
     * 入参可传 id（内部主键）或 userId（对外编码），二选一即可，库内统一存内部 id
     */
    @PostMapping("/cancelFollow")
    public BaseResponse<String> cancelFollowUser(Long id, Long userId){
        userFollowService.cancelFollowUser(id, userId);
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
