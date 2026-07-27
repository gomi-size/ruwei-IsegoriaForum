package com.ruwei.controller;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.dto.UserLoginDTO;
import com.ruwei.domain.dto.UserRegisterDTO;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.vo.LoginVO;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController("/user")
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

    @PostMapping("/login")
    public BaseResponse<UserVO> userLogin(
            @RequestBody  UserLoginDTO userLogin,
            HttpServletResponse response) { //  注入 Response 对象

        User user = userService.userLogin(userLogin);
        StpUtil.login(user.getId());
        String token = StpUtil.getTokenValue();

        // 将 Token 写入 HttpOnly Cookie（Web端自动携带，JS无法读取）
        Cookie cookie = new Cookie("satoken", token);
        cookie.setHttpOnly(true);       // 防 XSS 窃取
        cookie.setSecure(true);         // 仅 HTTPS 传输（生产环境必须开启）
        cookie.setPath("/");            // 全站有效
        cookie.setMaxAge(7200);// 与 Sa-Token timeout 保持一致
        // cookie.setSameSite("Lax");   // 防 CSRF（Servlet API 不直接支持，需手动拼接或用框架工具）
        response.addCookie(cookie);

        // 响应体中不再返回明文 Token，只返回用户基本信息
        UserVO userVO = BeanUtil.copyProperties(user, UserVO.class);
        return ResultUtils.success(userVO);
    }
}
