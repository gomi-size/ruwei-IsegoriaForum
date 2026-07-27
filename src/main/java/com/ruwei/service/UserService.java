package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.UserLoginDTO;
import com.ruwei.domain.dto.UserRegisterDTO;
import com.ruwei.domain.empty.User;


/**
* @author Administrator
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2026-07-22 14:07:39
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    User userRegister(UserRegisterDTO userRegisterDTO);

    /**
     * 用户登录
     * @param userLogin
     * @return
     */
    User userLogin(UserLoginDTO userLogin);
}
