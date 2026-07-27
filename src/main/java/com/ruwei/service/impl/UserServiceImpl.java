package com.ruwei.service.impl;


import cn.dev33.satoken.secure.BCrypt;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.StatusEnum;
import com.ruwei.domain.dto.UserLoginDTO;
import com.ruwei.domain.dto.UserRegisterDTO;
import com.ruwei.domain.empty.User;
import com.ruwei.service.UserService;
import com.ruwei.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-07-22 14:07:39
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    @Override
    public User userRegister(UserRegisterDTO userRegisterDTO) {
        //1.校验参数
        if (userRegisterDTO.getPassword()==null||userRegisterDTO.getUsername()==null||userRegisterDTO.getCheckPassword()==null){
            ThrowUtils.throwIf(true,ErrorCode.PARAMS_ERROR,"账号或密码不能为空");
        }
        // 用户名：6~12位，不能全为数字
        String username = userRegisterDTO.getUsername();
        ThrowUtils.throwIf(username.length() < 6 || username.length() > 12,
                ErrorCode.PARAMS_ERROR, "用户名长度必须为6~12位");
        ThrowUtils.throwIf(username.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "用户名不能全为数字");

        // 密码：8~12位，不能全为数字
        String password = userRegisterDTO.getPassword();
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");
        //确认密码
        String checkPassword = userRegisterDTO.getCheckPassword();
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");
        ThrowUtils.throwIf(!checkPassword.equals(password),ErrorCode.PARAMS_ERROR,"两次密码不相等");

        //2.加密密码
        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = BeanUtil.copyProperties(userRegisterDTO, User.class);
        user.setPassword(encryptedPassword);

        //3.TODO 设置userId需要使用redis
        user.setUserid("100000");

        //4.设置nikeName
        String pathName="ISEGORIA";
        String randomStr = RandomUtil.randomString(6);
        String nikeName=pathName+"_"+randomStr;
        user.setNickname(nikeName);

        //4.保存到数据库
        boolean result = save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"注册失败");


        return user;

    }

    /**
     * 用户登录
     * @param userLoginDTO
     * @return
     */
    @Override
    public User userLogin(UserLoginDTO userLoginDTO) {
        //1.校验参数
        if (userLoginDTO.getPassword()==null||userLoginDTO.getUsername()==null){
            ThrowUtils.throwIf(true,ErrorCode.PARAMS_ERROR,"账号或密码不能为空");
        }
        // 用户名：6~12位，不能全为数字
        String username = userLoginDTO.getUsername();
        ThrowUtils.throwIf(username.length() < 6 || username.length() > 12,
                ErrorCode.PARAMS_ERROR, "用户名长度必须为6~12位");
        ThrowUtils.throwIf(username.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "用户名不能全为数字");

        // 密码：8~12位，不能全为数字
        String password = userLoginDTO.getPassword();
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");

        //2.检查用户是否存在
        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

        LambdaQueryWrapper<User> lambdaQueryWrapper =new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getUsername,username);
        User user = baseMapper.selectOne(lambdaQueryWrapper);
        ThrowUtils.throwIf(user==null,ErrorCode.NOT_FOUND_ERROR,"用户不存在");

        //3.校验密码
        String userPassword = user.getPassword();
        ThrowUtils.throwIf(!encryptedPassword.equals(userPassword),ErrorCode.PARAMS_ERROR,"密码错误");

        //4.检查账号状态
        Integer status = user.getStatus();
        StatusEnum statusEnum = StatusEnum.getByCode(status);
        ThrowUtils.throwIf(!statusEnum.equals(StatusEnum.NORMAL),ErrorCode.USER_ERROR,"账号异常无法登录，请联系管理员");

        return user;
    }
}




