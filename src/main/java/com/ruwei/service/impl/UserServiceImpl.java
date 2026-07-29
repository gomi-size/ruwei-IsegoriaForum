package com.ruwei.service.impl;


import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.AdminEnum;
import com.ruwei.domain.Enum.StatusEnum;
import com.ruwei.domain.dto.UserEditDTO;
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
        user.setUserId("100001");

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
        LambdaQueryWrapper<User> lambdaQueryWrapper =new LambdaQueryWrapper<>();
        lambdaQueryWrapper.eq(User::getUsername,username);
        User user = baseMapper.selectOne(lambdaQueryWrapper);
        ThrowUtils.throwIf(user==null,ErrorCode.NOT_FOUND_ERROR,"用户不存在");

        //3.校验密码：用 checkpw 比对明文与库中 BCrypt 哈希（哈希串内含盐，自动取盐校验，禁止再 gensalt 重哈希）
        String userPassword = user.getPassword();
        ThrowUtils.throwIf(!BCrypt.checkpw(password, userPassword),ErrorCode.PARAMS_ERROR,"密码错误");

        //4.检查账号状态
        Integer status = user.getStatus();
        StatusEnum statusEnum = StatusEnum.getByCode(status);
        ThrowUtils.throwIf(!statusEnum.equals(StatusEnum.NORMAL),ErrorCode.USER_ERROR,"账号异常无法登录，请联系管理员");

        return user;
    }

    /**
     * 判断当前用户是不是管理员
     * @return
     */
    @Override
    public Boolean isAdmin() {
        // 先确认已登录
        StpUtil.checkLogin();
        //获取到当前的用户的id
        Long userId = StpUtil.getLoginIdAsLong();
        Integer status = getById(userId).getAdmin();
        return AdminEnum.getByCode(status).equals(AdminEnum.Admin);
    }

    /**
     * 管理员修改用户状态（禁用 / 启用 / 注销）
     */
    @Override
    public boolean updateUserStatus(Long userId, Integer status) {
        ThrowUtils.throwIf(userId == null || status == null || !StatusEnum.isValid(status),
                ErrorCode.PARAMS_ERROR, "参数不合法");
        User user = this.getById(userId);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        user.setStatus(status);
        return this.updateById(user);
    }

    /**
     * 用户编辑
     * @param userEditDTO
     */
    @Override
    public void editUserInfo(UserEditDTO userEditDTO) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(userEditDTO)||userEditDTO.getId()==null,ErrorCode.PARAMS_ERROR,"参数不能为空");
        long userId = StpUtil.getLoginIdAsLong();
        if (userId!=userEditDTO.getId()&&!isAdmin()){
            ThrowUtils.throwIf(true,ErrorCode.NO_AUTH_ERROR,"无权限,只有本人或者管理员");
        }
       ThrowUtils.throwIf(getById(userId)==null,ErrorCode.NOT_FOUND_ERROR,"无当前用户");

        // 生日：格式 yyyy-MM-dd（如 1990-05-20）
        ThrowUtils.throwIf(userEditDTO.getBirthday() != null && !userEditDTO.getBirthday().toString().matches("^\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])$"),
                ErrorCode.PARAMS_ERROR, "生日格式不正确，应为yyyy-MM-dd");

        // 个性签名：长度1-200个字符，不允许纯空白
        ThrowUtils.throwIf(userEditDTO.getBio() != null && !userEditDTO.getBio() .matches("^(?!\\s+$).{1,200}$"),
                ErrorCode.PARAMS_ERROR, "个性签名长度应在1-200个字符之间且不能为纯空白");

        // 所在地：长度1-100个字符，仅允许中文、字母、数字及常见地址符号
        ThrowUtils.throwIf(userEditDTO.getLocation()  != null && !userEditDTO.getLocation().matches("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-,.]{1,100}$"),
                ErrorCode.PARAMS_ERROR, "所在地格式不正确，仅允许中英文、数字及常见地址符号，长度1-100");

        // 手机号：中国大陆11位手机号
        ThrowUtils.throwIf(userEditDTO.getPhone()!= null && !userEditDTO.getPhone().matches("^1[3-9]\\d{9}$"),
                ErrorCode.PARAMS_ERROR, "手机号格式不正确");

        // 邮箱：通用邮箱格式校验
        ThrowUtils.throwIf(userEditDTO.getEmail() != null && !userEditDTO.getEmail().matches("^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$"),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");



        User userEdit = BeanUtil.copyProperties(userEditDTO, User.class);
        boolean result = updateById(userEdit);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"更新信息失败");
    }

    /**
     * 用户修改密码
     * @param id
     * @param password
     */
    @Override
    public void editUserPassword(Long id, String password) {
        ThrowUtils.throwIf(id==null||password==null,ErrorCode.PARAMS_ERROR,"参数不能为空");
        long userId = StpUtil.getLoginIdAsLong();
        if (userId!=id){
            ThrowUtils.throwIf(true,ErrorCode.NO_AUTH_ERROR,"无权限,只有本人");
        }
        ThrowUtils.throwIf(getById(userId)==null,ErrorCode.NOT_FOUND_ERROR,"无当前用户");
        // 密码：8~12位，不能全为数字
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");

        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User user=new User();
        user.setPassword(encryptedPassword);
        user.setId(id);
        boolean result = updateById(user);
        ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"修改密码未成功");
    }
}




