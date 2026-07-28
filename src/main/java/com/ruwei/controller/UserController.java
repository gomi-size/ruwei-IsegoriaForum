package com.ruwei.controller;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.UserLoginDTO;
import com.ruwei.domain.dto.UserRegisterDTO;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.vo.LoginVO;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<String> userRegister(@RequestBody UserRegisterDTO userRegisterDTO){

        User user= userService.userRegister(userRegisterDTO);
        StpUtil.login(user.getId());

        return ResultUtils.success("注册成功");
    }

    /**
     * 用户登录
     * @param userLogin
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(@RequestBody UserLoginDTO userLogin) {
        User user = userService.userLogin(userLogin);
        StpUtil.login(user.getId());
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        return ResultUtils.success(userVO);
    }

    /**
     * 用户退出登录
     * @return
     */
    @PostMapping("/out")
    public BaseResponse<String> userOut(){
        //先检查是否登录了
        StpUtil.checkLogin();
        //再退出登录
        StpUtil.logout();

        return ResultUtils.success("成功退出");
    }

    /**
     * 用户注销
     * @return
     */
    @PostMapping("/cancel")
    public BaseResponse<String> userCancel(){
        // 先确认已登录
        StpUtil.checkLogin();
        Long userId = StpUtil.getLoginIdAsLong();

        User user = userService.getById(userId);

        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        boolean result = userService.removeById(userId);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "用户注销失败");

        // 注销成功后，最后再清掉登录态
        StpUtil.logout();

        return ResultUtils.success("用户注销成功");
    }

    /**
     * 当前登录用户获取自己详情
     */
    @GetMapping("/userInfo")
    public BaseResponse<UserVO> getUserInfo(){
        // 先确认已登录
        StpUtil.checkLogin();
        //获取到当前的用户的id
        Long userId = StpUtil.getLoginIdAsLong();

        return ResultUtils.success(BeanUtil.copyProperties(userService.getById(userId),UserVO.class));


    }

}
