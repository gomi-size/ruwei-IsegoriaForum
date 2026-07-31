package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.dto.UserFollowOrFansPageDTO;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.UserFollowService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
     * 查看关注列表（分页）
     */
    @PostMapping("/getFollowUserList")
    public BaseResponse<IPage<UserVO>> getFollowUserList(@RequestBody UserFollowOrFansPageDTO userFollowOrFansPageDTO){

       IPage<UserVO> page = userFollowService.getFollowUserList(userFollowOrFansPageDTO);
       return ResultUtils.success(page);
    }

    /**
     * 查看粉丝列表（分页）
     */
    @PostMapping("/getFansUserList")
    public BaseResponse<IPage<UserVO>> getFansUserList(@RequestBody  UserFollowOrFansPageDTO userFollowOrFansPageDTO){
        IPage<UserVO> page = userFollowService.getFansUserList(userFollowOrFansPageDTO);
        return ResultUtils.success(page);
    }
}
