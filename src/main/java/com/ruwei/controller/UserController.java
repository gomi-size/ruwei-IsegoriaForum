package com.ruwei.controller;


import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.annotation.SaIgnore;
import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ruwei.annotation.RateLimit;
import com.ruwei.annotation.RateLimitDimension;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ResultUtils;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.EmailScene;
import com.ruwei.domain.dto.*;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.utils.ClientIpUtils;
import com.ruwei.domain.utils.QueryWrapperUtils;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.EmailCodeService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户的基础管理
 */
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private UserService userService;
    @Resource
    private EmailCodeService emailCodeService;


    /**
     * 用户注册
     * @param userRegisterDTO
     * @return
     */
    @PostMapping("/register")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 10, window = 600, prefix = "register")
    public BaseResponse<String> userRegister(@RequestBody UserRegisterDTO userRegisterDTO){

        User user= userService.userRegister(userRegisterDTO);
        StpUtil.login(user.getId());

        return ResultUtils.success("注册成功");
    }
    /**
     * 发送邮箱验证码。
     *
     * <p>未登录可调。限流分三层：</p>
     * <ul>
     *   <li>注解层 IP 维度：10 次/10 分钟 + 30 次/小时</li>
     *   <li>业务层邮箱维度：60 秒冷却 + 单邮箱日上限（注解层拿不到邮箱维度 ——
     *       本接口未登录，{@code RateLimitAspect} 会回退到 IP 维度）</li>
     *   <li>业务层 IP 维度日上限</li>
     * </ul>
     *
     * <p><b>响应文案与邮箱是否已注册无关</b>，一律返回成功 ——
     * 否则本接口会沦为「批量探测某邮箱是否注册过本站」的工具。</p>
     *
     * @param sendDTO 邮箱与场景
     * @param request 用于解析客户端 IP
     * @return 发送结果提示
     */
    @PostMapping("/email/code")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 10, window = 600, prefix = "emailCode")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 30, window = 3600, prefix = "emailCode")
    public BaseResponse<String> sendEmailCode(@RequestBody EmailCodeSendDTO sendDTO,
                                              HttpServletRequest request) {
        EmailScene scene = EmailScene.getByCode(sendDTO.getScene());
        ThrowUtils.throwIf(scene == null, ErrorCode.PARAMS_ERROR, "不支持的验证码场景");

        emailCodeService.sendCode(sendDTO.getEmail(), scene, ClientIpUtils.getClientIp(request));

        return ResultUtils.success("验证码已发送，请查收邮箱");
    }

    /**
     * 邮箱验证码登录。
     *
     * <p>与 {@link #userLogin} 并存：老用户继续走用户名密码，新用户可走邮箱验证码，
     * 两条通道互不影响，可按入口灰度回滚。</p>
     *
     * @param emailLoginDTO 邮箱与验证码
     * @return 登录用户信息
     */
    @PostMapping("/email/login")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 10, window = 600, prefix = "emailLogin")
    public BaseResponse<UserVO> emailLogin(@RequestBody EmailLoginDTO emailLoginDTO) {
        User user = userService.emailLogin(emailLoginDTO);
        StpUtil.login(user.getId());
        return ResultUtils.success(BeanUtil.copyProperties(user, UserVO.class));
    }

    /**
     * 用户登录
     * @param userLogin
     * @return
     */
    @PostMapping("/login")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 10, window = 600, prefix = "login")
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
    @RateLimit(limit = 1, window = 60, prefix = "userCancel")
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
     * 当前登录用户获取自己详情
     */
    @GetMapping("/userInfo")
    public BaseResponse<UserVO> getUserVOInfo(){
        // @SaCheckLogin 已保证登录态
        Long id = StpUtil.getLoginIdAsLong();

        return ResultUtils.success(BeanUtil.copyProperties(userService.getById(id),UserVO.class));
    }

    /**
     * 当前登录用户获取别人的详情详情
     * <p>入参兼容<b>对外编码 userId 与内部主键 id</b>：他人主页可传对外编码；
     * 帖子作者/关注列表等场景前端拿到的是内部雪花 id，同样可直接传入。</p>
     */
    @GetMapping("/otherUserInfo")
    @SaIgnore
    public BaseResponse<UserVO> getOtherUserVOInfo(Long userId){
        UserVO userVO =userService.getOtherUserVOInfo(userId);
        return ResultUtils.success(userVO);
    }


    /**
     * 用户编辑信息
     * @param userEditDTO
     * @return
     */
    @SaCheckLogin
    @PostMapping("/edit")
    @RateLimit(limit = 10, window = 60, prefix = "userEdit")
    public BaseResponse<String> editUserInfo(@RequestBody UserEditDTO userEditDTO){
        userService.editUserInfo(userEditDTO);
        return ResultUtils.success("更新成功");
    }


    /**
     * 修改密码（已登录场景，只能改本人的）。
     *
     * <p><b>契约变更（前端需同步调整）</b>：入参由 query 参数 {@code ?id=&password=}
     * 改为 JSON 请求体 {@link EditPasswordDTO}，并新增 {@code checkPassword} 二次确认。
     * {@code id} 一律传<b>内部主键 id</b>（即登录接口返回的用户对象里的 {@code id} 字段），
     * 不要传对外编码 {@code userId}；且必须与当前登录用户一致，否则返回 40300。</p>
     *
     * <p><b>行为变更</b>：修改成功后服务端会销毁该账号全部会话
     * （与「忘记密码」一致），当前 Cookie 立即失效，前端需引导用户重新登录。</p>
     *
     * @param editPasswordDTO 目标用户内部 id、新密码与确认密码
     * @return 修改结果提示
     */
    @SaCheckLogin
    @PostMapping("/editPassword")
    @RateLimit(limit = 5, window = 60, prefix = "password")
    public BaseResponse<String> editUserPassword(@RequestBody EditPasswordDTO editPasswordDTO){
        userService.editUserPassword(editPasswordDTO);
        return ResultUtils.success("修改密码成功，请重新登录");
    }

    /**
     * 绑定（换绑）邮箱（已登录场景）。
     *
     * <p>身份凭证为「登录态 + 新邮箱验证码」（场景 {@code bindEmail}）：
     * 需先调用 {@code POST /user/email/code} 向新邮箱发码。换绑成功后
     * 旧邮箱立即无法再用于验证码登录与找回密码。</p>
     *
     * @param emailBindDTO 新邮箱与验证码
     * @return 绑定结果提示
     */
    @SaCheckLogin
    @PostMapping("/bindEmail")
    @RateLimit(limit = 5, window = 60, prefix = "bindEmail")
    public BaseResponse<String> bindEmail(@RequestBody EmailBindDTO emailBindDTO){
        userService.bindEmail(emailBindDTO);
        return ResultUtils.success("邮箱绑定成功");
    }

    /**
     * 忘记密码：凭邮箱验证码重置密码（未登录场景）。
     *
     * <p>身份凭证为「邮箱 + 该邮箱收到的验证码」，验证码场景固定为
     * {@code EmailScene.RESET_PASSWORD}，需先调用发码接口获取验证码。</p>
     *
     * <p><b>契约变更（前端需同步调整）</b>：入参由 query 参数 {@code ?userId=&Password=}
     * 改为 JSON 请求体 {@link EmailResetPasswordDTO}；返回体由 {@code Boolean}
     * 改为提示文案 {@code String}。原入参形式无任何身份校验，属越权漏洞，不可继续沿用。</p>
     *
     * @param resetPasswordDTO 邮箱、验证码、新密码与确认密码
     * @return 重置结果提示
     */
    @PostMapping("/forgetPassword")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 1, window = 60, prefix = "forget")
    @RateLimit(dimension = RateLimitDimension.IP, limit = 5, window = 3600, prefix = "forget")
    public BaseResponse<String> forgetPassword(@RequestBody EmailResetPasswordDTO resetPasswordDTO){
        userService.forgetPassword(resetPasswordDTO);
        return ResultUtils.success("密码重置成功");
    }
}
