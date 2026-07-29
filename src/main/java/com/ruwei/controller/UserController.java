package com.ruwei.controller;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.UserEditDTO;
import com.ruwei.domain.dto.UserLoginDTO;
import com.ruwei.domain.dto.UserRegisterDTO;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.vo.LoginVO;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.expression.spel.ast.BeanReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
     * 用户退出登录（需登录）
     * @return
     */
    @SaCheckLogin
    @PostMapping("/out")
    public BaseResponse<String> userOut(){

        StpUtil.logout();

        return ResultUtils.success("成功退出");
    }

    /**
     * 用户注销（需登录，且只能注销自己）
     * @return
     */
    @SaCheckLogin
    @PostMapping("/cancel")
    public BaseResponse<String> userCancel(){
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
     * 当前登录用户获取自己详情（需登录）
     */
    @SaCheckLogin
    @GetMapping("/userInfo")
    public BaseResponse<UserVO> getUserVOInfo(){
        // @SaCheckLogin 已保证登录态
        Long userId = StpUtil.getLoginIdAsLong();

        return ResultUtils.success(BeanUtil.copyProperties(userService.getById(userId),UserVO.class));
    }

    /**
     * 管理员：修改指定用户的状态（禁用 / 启用 / 注销）—— 对应“用户状态权”
     * 仅管理员可访问 —— @SaCheckRole("admin")
     */
    @SaCheckRole("admin")
    @PostMapping("/status")
    public BaseResponse<String> updateUserStatus(@RequestParam Long Id,
                                                 @RequestParam Integer status) {
        ThrowUtils.throwIf(Id==null||status==null,ErrorCode.PARAMS_ERROR,"有一个为空");
        boolean result = userService.updateUserStatus(Id, status);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "修改用户状态失败");
        return ResultUtils.success("修改成功");
    }

    /**
     * 管理员查看所有用户列表
     * @return
     */
    @SaCheckRole("admin")
    @GetMapping("/list")
    public BaseResponse<List<User>> listAllUsers() {
        List<User> users = userService.list();
        return ResultUtils.success(users);
    }

    /**
     * 管理员：查看任意指定用户的完整信息
     * 仅管理员可访问 —— @SaCheckRole("admin")（普通用户看自己请用 /user/userInfo）
     */
    @SaCheckRole("admin")
    @GetMapping("/{id}")
    public BaseResponse<User> getUserInfo(@PathVariable Long id) {
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return ResultUtils.success(user);
    }

    /**
     * 用户编辑信息
     * @param userEditDTO
     * @return
     */
    @SaCheckLogin
    @PostMapping("/edit")
    public BaseResponse<String> editUserInfo(@RequestBody UserEditDTO userEditDTO){
        userService.editUserInfo(userEditDTO);
        return ResultUtils.success("更新成功");
    }

    @SaCheckLogin
    @PostMapping("/editPassword")
    public BaseResponse<String> editUserPassword(Long id,String password){
        userService.editUserPassword(id,password);
        return ResultUtils.success("修改密码成功");
    }

}
