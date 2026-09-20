package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.*;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.vo.UserVO;


/**
* @author chenhang
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

    /**
     * 用户使用邮箱验证码登录。
     *
     * <p>与 {@link #userLogin} 的区别：{@link #userLogin} 校验「用户名 + 密码」，
     * 本方法校验「邮箱 + 该邮箱收到的验证码」，两者共用同一套登录态建立流程。</p>
     *
     * @param emailLoginDTO 邮箱与验证码
     * @return 命中的用户实体
     */
    User emailLogin(EmailLoginDTO emailLoginDTO);


    /**
     * 判断是否为管理员
     * @return
     */
    Boolean isAdmin();

    /**
     * 管理员修改用户状态
     * @param userId 目标用户 id
     * @param status 目标状态（StatusEnum.code）
     * @return 是否修改成功
     */
    boolean updateUserStatus(Long userId, Integer status);

    /**
     * 管理员：设置 / 取消指定用户的管理员身份（对应 user 表 admin 标志位）。
     *
     * <p>角色本身由 {@code StpInterfaceImpl} 在每次鉴权时<b>实时</b>读取 admin 字段，
     * 因此目标用户无需重新登录，改完下一次请求即生效。</p>
     *
     * <p>两条业务防护：不允许管理员取消自己的管理员身份；取消时须保证系统中
     * 至少保留一名管理员，避免后台被彻底锁死。</p>
     *
     * @param userId 目标用户（兼容对外编码 userId 与内部主键 id）
     * @param admin  目标身份：AdminEnum.Admin(1) 设为管理员，AdminEnum.User(0) 取消管理员
     * @return 是否修改成功（目标已是该身份时返回 true，幂等）
     */
    boolean updateUserAdmin(Long userId, Integer admin);

    /**
     * 用户编辑
     * @param userEditDTO
     */
    void editUserInfo(UserEditDTO userEditDTO);

    /**
     * 用户修改密码
     * @param id
     * @param password
     */
    void editUserPassword(Long id, String password);

    /**
     * 忘记密码：凭邮箱验证码重置密码（未登录场景）。
     *
     * <p>必须以「邮箱 + 该邮箱收到的验证码」作为身份凭证 —— 原实现仅凭 {@code userId}
     * 即可重置任意账号密码，属可被利用的越权漏洞，已整体废弃。</p>
     *
     * @param resetPasswordDTO 邮箱、验证码、新密码与确认密码
     */
    void forgetPassword(EmailResetPasswordDTO resetPasswordDTO);

    /**
     * 管理员重置指定用户的密码（后台运维场景）。
     *
     * <p>与 {@link #forgetPassword} 的区别：本方法由已鉴权的管理员调用，
     * 管理员权限本身即身份凭证，因此无需邮箱验证码；调用方必须标注
     * {@code @SaCheckRole("admin")}，否则等同于开放越权重置入口。</p>
     *
     * @param userId   目标用户（兼容对外编码 userId 与内部主键 id）
     * @param password 新密码
     */
    void adminResetPassword(Long userId, String password);

    /**
     * 当前登录用户获取别人的详情（按对外编码 userId 查找，供前端直接调用于查看他人主页）
     * @param userId 对方的对外编码
     */
    UserVO getOtherUserVOInfo(Long userId);

    /**
     * 当前登录用户获取别人的详情（按内部主键 id 查找）。
     * 关注列表/粉丝列表内部统一以内部 id 索引，渲染 VO 时使用本方法。
     * @param id 对方的内部主键
     */
    UserVO getOtherUserVOInfoById(Long id);


}
