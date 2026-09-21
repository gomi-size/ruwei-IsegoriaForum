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
import com.ruwei.domain.Enum.EmailScene;
import com.ruwei.domain.Enum.StatusEnum;
import com.ruwei.domain.dto.*;
import com.ruwei.component.SensitiveWordFilter;
import com.ruwei.domain.empty.Post;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.empty.UserFollow;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.es.event.UserProfileUpdatedEvent;
import com.ruwei.mapper.UserFollowMapper;
import com.ruwei.service.EmailCodeService;
import com.ruwei.service.PostService;
import com.ruwei.service.UserService;
import com.ruwei.mapper.UserMapper;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
* @author Administrator
* @description 针对表【user(用户表)】的数据库操作Service实现
* @createDate 2026-07-22 14:07:39
*/
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private UserFollowMapper userFollowMapper;

    /**
     * 邮箱验证码服务：用于忘记密码等场景的身份校验与验证码消费。
     */
    @Resource
    private EmailCodeService emailCodeService;

    /**
     * userId 自增计数器（对外编码），基于 Redis 原子自增，保证集群/并发下不重复。
     */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    @Resource
    @Lazy
    private PostService postService;

    /**
     * userId 计数器在 Redis 中的 key。
     */
    private static final String USER_ID_COUNTER_KEY = "isegoria:user:id:counter";

    /**
     * userId 默认起始基准（库内无数据时从此起步）。
     */
    private static final long USER_ID_BASE = 100000L;

    /**
     * 邮箱格式正则：通用邮箱校验。
     * <p>收口为常量，避免注册、编辑资料、重置密码等多处各写一份导致规则漂移。</p>
     */
    private static final String EMAIL_REGEX = "^[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}$";

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
        //校验邮箱
        String email = userRegisterDTO.getEmail();
        ThrowUtils.throwIf(email == null || !email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");

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

        boolean exists = lambdaQuery().eq(User::getUsername, username).exists();
        ThrowUtils.throwIf(exists,ErrorCode.OPERATION_ERROR,"用户名已被注册了");

        //检查邮箱唯一性
        boolean emailExists = lambdaQuery().eq(User::getEmail, email).exists();
        ThrowUtils.throwIf(emailExists, ErrorCode.OPERATION_ERROR, "该邮箱已被注册，请直接登录或使用其它邮箱");

        //检测验证码是否正确并消费
        emailCodeService.consumeCode(email, EmailScene.REGISTER, userRegisterDTO.getCode());
        //2.加密密码
        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        User user = BeanUtil.copyProperties(userRegisterDTO, User.class);
        user.setPassword(encryptedPassword);

        //3.设置 userId：基于 Redis 原子自增，每次注册 +1（首次以库内最大 userId 起步，避免与历史数据冲突）
        user.setUserId(generateUserId());

        //4.设置nikeName
        String pathName="ISEGORIA";
        String randomStr = RandomUtil.randomString(6);
        String nikeName=pathName+"_"+randomStr;
        user.setNickname(nikeName);

        user.setEmail(email);
        //4.保存到数据库
        boolean result = save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR,"注册失败");

        return user;

    }



    /**
     * 用户使用账号密码登录
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
     * 用户使用邮箱登录
     */
    @Override
    public User emailLogin(EmailLoginDTO emailLoginDTO) {
        //1.校验参数
        ThrowUtils.throwIf(BeanUtil.isEmpty(emailLoginDTO), ErrorCode.PARAMS_ERROR, "参数不能为空");
        String email = emailLoginDTO.getEmail();
        String code = emailLoginDTO.getCode();
        ThrowUtils.throwIf(StrUtil.isBlank(email) || StrUtil.isBlank(code),
                ErrorCode.PARAMS_ERROR, "邮箱与验证码均不能为空");
        ThrowUtils.throwIf(!email.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");

        emailCodeService.consumeCode(email, EmailScene.LOGIN, code);

        // 2.按邮箱定位账号
        User user = lambdaQuery().eq(User::getEmail, email).one();
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR, "该邮箱尚未注册");

        // 3.校验账号状态
        //    注意 status 可能为 null，getByCode 会返回 null —— 此处必须显式判空，
        //    否则直接用 statusEnum.equals(...) 会 NPE
        StatusEnum statusEnum = user.getStatus() == null ? null : StatusEnum.getByCode(user.getStatus());
        ThrowUtils.throwIf(!StatusEnum.NORMAL.equals(statusEnum),
                ErrorCode.USER_ERROR, "账号异常无法登录，请联系管理员");

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
     * 管理员：设置 / 取消指定用户的管理员身份（对应 user 表 admin 标志位）。
     *
     * <p><b>入参兼容双主键</b>：先按对外编码 {@code userId} 查询，查不到再按内部主键 {@code id}
     * 查询（两种 id 数值域不重叠，不会误匹配）；与 {@link #adminResetPassword} 保持同一套解析规则，
     * 因为后台用户列表返回的是内部 id、而用户主页拿到的是对外编码。</p>
     *
     * <p><b>为什么这个方法是高危方法</b>：admin 是 Sa-Token 角色体系的唯一数据源
     * （见 {@code StpInterfaceImpl#getRoleList}），能写这个字段就等于能授予 / 收回后台权限，
     * 因此调用方必须标注 {@code @SaCheckRole("admin")}，否则等同于开放「任意用户自我提权」入口。</p>
     *
     * <p><b>两条防护</b>：</p>
     * <ul>
     *   <li>不允许管理员取消自己的管理员身份 —— 防止手滑把自己踢出后台；</li>
     *   <li>取消管理员时校验系统内管理员数量，至少保留一名 —— 防止后台被彻底锁死。</li>
     * </ul>
     *
     * <p><b>生效时机</b>：Sa-Token 1.41 未启用角色 / 权限缓存，每次鉴权都会回调
     * {@code StpInterfaceImpl} 现查数据库，因此改完<b>无需目标用户重新登录</b>，
     * 他的下一次请求就会带上新的 admin 角色。</p>
     *
     * @param userId 目标用户（兼容对外编码 userId 与内部主键 id）
     * @param admin  目标身份：{@link AdminEnum#Admin}(1) 设为管理员，{@link AdminEnum#User}(0) 取消管理员
     * @return 是否修改成功（目标已是该身份时直接返回 true，保证幂等）
     */
    @Override
    public boolean updateUserAdmin(Long userId, Integer admin) {
        ThrowUtils.throwIf(userId == null || admin == null || !AdminEnum.isValid(admin),
                ErrorCode.PARAMS_ERROR, "参数不合法");

        // 兼容双主键：先按对外编码 userId 查，查不到再按内部主键 id 查
        User user = lambdaQuery().eq(User::getUserId, userId).one();
        if (BeanUtil.isEmpty(user)) {
            user = getById(userId);
        }
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 幂等：目标已是该身份，直接返回成功，避免无意义的 update 与前端误报失败
        int current = user.getAdmin() != null ? user.getAdmin() : AdminEnum.User.getCode();
        if (current == admin) {
            return true;
        }

        // 防护 1：不允许管理员取消自己，防止误操作把自己踢出后台
        ThrowUtils.throwIf(AdminEnum.isAdmin(current) && !AdminEnum.isAdmin(admin)
                        && Objects.equals(user.getId(), StpUtil.getLoginIdAsLong()),
                ErrorCode.NO_AUTH_ERROR, "不能取消自己的管理员身份");

        // 防护 2：取消管理员时须保证系统仍有其他管理员，避免后台被彻底锁死
        if (!AdminEnum.isAdmin(admin)) {
            long adminCount = lambdaQuery().eq(User::getAdmin, AdminEnum.Admin.getCode()).count();
            ThrowUtils.throwIf(adminCount <= 1, ErrorCode.OPERATION_ERROR, "系统至少需保留一名管理员");
        }

        boolean result = lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getAdmin, admin)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "修改管理员身份失败");

        // 提权 / 降权是敏感操作，留一条服务端日志便于事后追溯
        log.info("管理员 {} 将用户 {}(id={}) 的管理员身份修改为 {}", StpUtil.getLoginIdAsLong(),
                user.getUserId(), user.getId(), admin);
        return true;
    }

    /**
     * 用户编辑
     * @param userEditDTO
     */
    @Override
    public void editUserInfo(UserEditDTO userEditDTO) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(userEditDTO)||userEditDTO.getId()==null,ErrorCode.PARAMS_ERROR,"参数不能为空");
        long id = StpUtil.getLoginIdAsLong();
        if (id !=userEditDTO.getId()&&!isAdmin()){
            ThrowUtils.throwIf(true,ErrorCode.NO_AUTH_ERROR,"无权限,只有本人或者管理员");
        }
       ThrowUtils.throwIf(getById(id)==null,ErrorCode.NOT_FOUND_ERROR,"无当前用户");

        // 昵称：长度4-12个字符；不传/为空则不校验也不更新；不允许纯空白
        if(!getById(id).getNickname().equals(userEditDTO.getNickname())){
            ThrowUtils.throwIf(StrUtil.isNotBlank(userEditDTO.getNickname()) && !userEditDTO.getNickname().matches("^(?!\\s+$).{4,12}$"),
                    ErrorCode.PARAMS_ERROR, "昵称长度应在4-12个字符之间且不能为纯空白");
        }
        // 生日：格式 yyyy-MM-dd（如 1990-05-20）；不传则不校验
        ThrowUtils.throwIf(userEditDTO.getBirthday() != null && !userEditDTO.getBirthday().toString().matches("^\\d{4}-(?:0[1-9]|1[0-2])-(?:0[1-9]|[12]\\d|3[01])$"),
                ErrorCode.PARAMS_ERROR, "生日格式不正确，应为yyyy-MM-dd");

        // 个性签名：长度1-200个字符；不传/为空则不校验
        ThrowUtils.throwIf(StrUtil.isNotBlank(userEditDTO.getBio()) && !userEditDTO.getBio().matches("^(?!\\s+$).{1,200}$"),
                ErrorCode.PARAMS_ERROR, "个性签名长度应在1-200个字符之间且不能为纯空白");

        // 所在地：长度1-100个字符，仅允许中文、字母、数字及常见地址符号；不传/为空则不校验
        ThrowUtils.throwIf(StrUtil.isNotBlank(userEditDTO.getLocation()) && !userEditDTO.getLocation().matches("^[\\u4e00-\\u9fa5a-zA-Z0-9\\s\\-,.]{1,100}$"),
                ErrorCode.PARAMS_ERROR, "所在地格式不正确，仅允许中英文、数字及常见地址符号，长度1-100");

        // 手机号：中国大陆11位手机号；不传/为空则不校验
        ThrowUtils.throwIf(StrUtil.isNotBlank(userEditDTO.getPhone()) && !userEditDTO.getPhone().matches("^1[3-9]\\d{9}$"),
                ErrorCode.PARAMS_ERROR, "手机号格式不正确");

        // 邮箱：通用邮箱格式校验；不传/为空则不校验
        ThrowUtils.throwIf(StrUtil.isNotBlank(userEditDTO.getEmail()) && !userEditDTO.getEmail().matches(EMAIL_REGEX),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");

        // ===== 敏感词/违禁词检查（新增）=====
        if (StrUtil.isNotBlank(userEditDTO.getNickname())) {
            sensitiveWordFilter.checkStrict(userEditDTO.getNickname(), "昵称");
        }
        if (StrUtil.isNotBlank(userEditDTO.getBio())) {
            sensitiveWordFilter.checkStrict(userEditDTO.getBio(), "个性签名");
        }
        if (StrUtil.isNotBlank(userEditDTO.getLocation())) {
            sensitiveWordFilter.checkStrict(userEditDTO.getLocation(), "所在地");
        }

        User user = BeanUtil.copyProperties(userEditDTO, User.class);
        boolean result = updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "更新信息失败");

        // 用户资料变更 → 异步重建该作者全部 ES 帖子（昵称/头像冗余在 PostDoc，需重新索引）
        eventPublisher.publishEvent(new UserProfileUpdatedEvent(this, userEditDTO.getId()));
    }

    /**
     * 用户修改自己的密码（已登录场景，需当前绑定邮箱验证码）。
     *
     * <p><b>身份凭证升级</b>：在登录态之外新增邮箱验证码（场景
     * {@code EmailScene.CHANGE_PASSWORD}）—— 仅凭登录态即可改密的旧流程已废弃。
     * 验证码发送至当前绑定邮箱，邮箱由服务端从登录态读取（前端只传 code），
     * 防止校验被指向他人邮箱；消费顺序放在密码强度校验之后，弱密码请求
     * 不会白白消耗一次验证码。</p>
     *
     * <p><b>本次修复的两个缺陷</b>：</p>
     * <ol>
     *   <li><b>id 语义错配</b>：原实现拿入参 {@code id} 与 {@code StpUtil.getLoginIdAsLong()}
     *       直接比对。登录态里存的是<b>内部雪花主键</b>，而前端在多数场景持有的是
     *       <b>对外编码 userId</b>（base 100000），两者数值域不重叠，
     *       用 {@code !=} 比较必然失败，本人改密码也会被判「无权限」。
     *       现统一按内部主键传参并做显式等值校验。</li>
     *   <li><b>缺少密码一致性校验</b>：原实现只收一个 {@code password}，
     *       前端两次输入不一致时无服务端兜底。现补 {@code checkPassword} 校验。</li>
     * </ol>
     *
     * <p><b>为何要在改密后登出</b>：与 {@link #forgetPassword} 对齐 ——
     * 密码已变更，旧登录态必须失效，否则凭旧会话仍可持续访问。</p>
     *
     * @param editPasswordDTO 目标用户内部 id、邮箱验证码、新密码与确认密码
     */
    @Override
    public void editUserPassword(EditPasswordDTO editPasswordDTO) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(editPasswordDTO)
                        || editPasswordDTO.getId() == null
                        || StrUtil.isBlank(editPasswordDTO.getPassword())
                        || StrUtil.isBlank(editPasswordDTO.getCode()),
                ErrorCode.PARAMS_ERROR, "参数不能为空");

        String password = editPasswordDTO.getPassword();
        String checkPassword = editPasswordDTO.getCheckPassword();

        // 1.越权校验：登录态中的内部 id 必须与入参一致。
        //   getLoginIdAsLong() 返回基本类型 long，需显式装箱后再比较，
        //   否则 Objects.equals(Object, Object) 在 Long 缓存区间（-128~127）之外
        //   会退化为引用比较，雪花 id 必然不等而误判「无权限」。
        Long loginId = StpUtil.getLoginIdAsLong();
        ThrowUtils.throwIf(!Objects.equals(loginId, editPasswordDTO.getId()),
                ErrorCode.NO_AUTH_ERROR, "无权限，只能修改本人的密码");

        long targetId = loginId;
        User current = getById(targetId);
        ThrowUtils.throwIf(current == null, ErrorCode.NOT_FOUND_ERROR, "当前用户不存在");

        // 2.密码强度：与注册、找回密码保持一致
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");
        ThrowUtils.throwIf(!password.equals(checkPassword),
                ErrorCode.PARAMS_ERROR, "两次密码不相等");

        // 3.邮箱验证码校验（场景 changePwd）：必须凭当前绑定邮箱收到的验证码才能改密。
        //   邮箱由服务端从登录态取，不接受前端传参 —— 否则验证码消费可被指向任意邮箱。
        emailCodeService.consumeCode(current.getEmail(), EmailScene.CHANGE_PASSWORD, editPasswordDTO.getCode());

        // 4.BCrypt 加密后写入（哈希串内含盐，禁止明文落库）
        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        boolean result = lambdaUpdate()
                .eq(User::getId, targetId)
                .set(User::getPassword, encryptedPassword)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "修改密码未成功");

        // 5.密码已变更 → 销毁该账号全部历史会话，旧登录态立即失效。
        //   注意：登出后当前请求的 Cookie 即失效，前端需引导用户重新登录。
        StpUtil.logout(targetId);
    }

    /**
     * 绑定（换绑）邮箱（已登录场景）。
     *
     * <p><b>身份凭证</b>：登录态 + 新邮箱收到的 {@code EmailScene.BIND_EMAIL}
     * 验证码，因此无需旧邮箱配合；换绑成功后旧邮箱立即失效（验证码登录、
     * 找回密码均按 user.email 当前值定位账号）。</p>
     *
     * <p><b>顺序说明</b>：先消费验证码再查重，与 {@link #forgetPassword} 同一原则 ——
     * 不持有新邮箱验证码的请求无法探测某邮箱是否已被占用，防邮箱枚举。</p>
     *
     * @param emailBindDTO 新邮箱与验证码
     */
    @Override
    public void bindEmail(EmailBindDTO emailBindDTO) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(emailBindDTO), ErrorCode.PARAMS_ERROR, "参数不能为空");

        String email = emailBindDTO.getEmail();
        String code = emailBindDTO.getCode();
        ThrowUtils.throwIf(StrUtil.isBlank(email) || StrUtil.isBlank(code),
                ErrorCode.PARAMS_ERROR, "邮箱与验证码均不能为空");
        ThrowUtils.throwIf(!email.matches(EMAIL_REGEX),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");

        long loginId = StpUtil.getLoginIdAsLong();
        User current = getById(loginId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(current), ErrorCode.NOT_FOUND_ERROR, "当前用户不存在");

        // 与当前邮箱相同（忽略大小写）则直接提示，避免白白消费一次验证码
        ThrowUtils.throwIf(email.equalsIgnoreCase(current.getEmail()),
                ErrorCode.PARAMS_ERROR, "新邮箱与当前邮箱相同，无需更换");

        // 1.消费新邮箱的 bindEmail 场景验证码（先于查重，防邮箱枚举）
        emailCodeService.consumeCode(email, EmailScene.BIND_EMAIL, code);

        // 2.新邮箱不能已被其它账号占用（与注册的邮箱唯一性校验同一口径）
        boolean emailExists = lambdaQuery().eq(User::getEmail, email).exists();
        ThrowUtils.throwIf(emailExists, ErrorCode.OPERATION_ERROR, "该邮箱已被其它账号绑定");

        // 3.更新当前登录用户的邮箱
        boolean result = lambdaUpdate()
                .eq(User::getId, loginId)
                .set(User::getEmail, email)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "绑定邮箱失败");

        log.info("用户换绑邮箱成功，内部 id={}", loginId);
    }

    /**
     * 忘记密码：凭邮箱验证码重置密码（未登录场景）。
     *
     * <p><b>修复说明</b>：原实现存在三个缺陷，本次一并修正 ——</p>
     * <ol>
     *   <li><b>越权</b>：接口无任何身份校验，任何人知道 {@code userId} 即可重置他人密码。
     *       现改为以「邮箱 + 该邮箱收到的验证码」作为身份凭证。</li>
     *   <li><b>条件恒假</b>：{@code eq(User::getPassword, password)} 拿明文去比库中的 BCrypt 哈希，
     *       条件永远不成立，更新从未真正生效。</li>
     *   <li><b>明文落库</b>：{@code set(User::getPassword, password)} 写入的是明文而非 BCrypt 哈希，
     *       即便前一条修好，该账号也会永久无法登录。</li>
     * </ol>
     *
     * @param resetPasswordDTO 邮箱、验证码、新密码与确认密码
     */
    @Override
    public void forgetPassword(EmailResetPasswordDTO resetPasswordDTO) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(resetPasswordDTO), ErrorCode.PARAMS_ERROR, "参数不能为空");

        String email = resetPasswordDTO.getEmail();
        String code = resetPasswordDTO.getCode();
        String password = resetPasswordDTO.getPassword();
        String checkPassword = resetPasswordDTO.getCheckPassword();

        // 1.参数校验
        ThrowUtils.throwIf(StrUtil.isBlank(email) || StrUtil.isBlank(code)
                        || StrUtil.isBlank(password) || StrUtil.isBlank(checkPassword),
                ErrorCode.PARAMS_ERROR, "邮箱、验证码、新密码均不能为空");
        ThrowUtils.throwIf(!email.matches(EMAIL_REGEX),
                ErrorCode.PARAMS_ERROR, "邮箱格式不正确");

        // 2.密码强度：与注册、修改密码保持一致
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");
        ThrowUtils.throwIf(!checkPassword.equals(password),
                ErrorCode.PARAMS_ERROR, "两次密码不相等");

        // 3.先校验并消费验证码，再查用户是否存在。
        //   顺序不可调换：若先查用户并返回「该邮箱未注册」，攻击者无需持有任何验证码
        //   即可用本接口批量探测哪些邮箱注册过本站（邮箱枚举）。
        emailCodeService.consumeCode(email, EmailScene.RESET_PASSWORD, code);

        // 4.定位账号并校验状态
        User user = lambdaQuery().eq(User::getEmail, email).one();
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR, "该邮箱尚未注册");

        StatusEnum statusEnum = user.getStatus() == null ? null : StatusEnum.getByCode(user.getStatus());
        ThrowUtils.throwIf(!StatusEnum.NORMAL.equals(statusEnum),
                ErrorCode.USER_ERROR, "账号异常，无法重置密码，请联系管理员");

        // 5.BCrypt 加密后写入。此处必须加密：原实现直接写入明文，
        //   会导致该账号在后续登录时 BCrypt.checkpw 全部失败（永久无法登录）。
        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        boolean result = lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPassword, encryptedPassword)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "重置密码失败");

        // 6.重置成功即销毁该账号全部历史会话，防止旧登录态在密码已变更后继续有效
        StpUtil.logout(user.getId());
    }

    /**
     * 管理员重置指定用户的密码（后台运维场景）。
     *
     * <p>入参 {@code userId} 兼容对外编码与内部主键：先按对外编码查询，
     * 查不到再按内部主键查询（两种 id 数值域不重叠，不会误匹配）。</p>
     *
     * @param userId   目标用户对外编码或内部主键
     * @param password 新密码
     */
    @Override
    public void adminResetPassword(Long userId, String password) {
        ThrowUtils.throwIf(userId == null || StrUtil.isBlank(password),
                ErrorCode.PARAMS_ERROR, "用户与密码不能为空");

        // 密码强度：与注册、修改密码保持一致
        ThrowUtils.throwIf(password.length() < 8 || password.length() > 12,
                ErrorCode.PARAMS_ERROR, "密码长度必须为8~12位");
        ThrowUtils.throwIf(password.matches("^\\d+$"),
                ErrorCode.PARAMS_ERROR, "密码不能全为数字");

        User user = lambdaQuery().eq(User::getUserId, userId).one();
        if (BeanUtil.isEmpty(user)) {
            // 兼容内部主键传参（后台用户列表返回的是内部 id）
            user = getById(userId);
        }
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR, "用户不存在");

        // 必须 BCrypt 加密后写入，禁止明文落库
        String encryptedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
        boolean result = lambdaUpdate()
                .eq(User::getId, user.getId())
                .set(User::getPassword, encryptedPassword)
                .update();
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "重置密码失败");

        // 重置成功即销毁该账号全部历史会话，使泄露的旧凭据立即失效
        StpUtil.logout(user.getId());
        log.info("管理员重置用户密码成功，目标用户内部 id={}", user.getId());
    }

    /**
     * 当前登录用户获取别人的详情详情（需登录）
     * <p>入参兼容<b>对外编码 userId 与内部主键 id</b>：先按对外编码查询（他人主页常用入口），
     * 查不到时再按内部主键查询（帖子/关注列表等场景前端拿到的作者 id 是内部雪花 id）。
     * 两种 id 数值域不重叠（内部 id 约 19 位、对外编码 10 万起步），不会误匹配。
     * 均查不到时抛「用户不存在」，避免底层 copyProperties 返回 null 后 setter 触发 NPE。</p>
     * @param userId 对方对外编码或内部主键
     */
    @Override
    public UserVO getOtherUserVOInfo(Long userId) {
        ThrowUtils.throwIf(userId == null, ErrorCode.PARAMS_ERROR, "请传入对方 userId 或内部 id");
        User otherUser = lambdaQuery().eq(User::getUserId, userId).one();
        if (BeanUtil.isEmpty(otherUser)) {
            // 兼容内部 id 传参（帖子作者/关注列表里的内部雪花 id）
            otherUser = getById(userId);
        }
        ThrowUtils.throwIf(BeanUtil.isEmpty(otherUser), ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return buildOtherUserVO(otherUser);
    }

    /**
     * 按内部主键 id 获取他人详情（关注/粉丝列表内部使用）。
     * @param id 对方内部主键
     */
    @Override
    public UserVO getOtherUserVOInfoById(Long id) {
        User otherUser = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(otherUser), ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return buildOtherUserVO(otherUser);
    }

    /**
     * 组装他人 VO 并计算当前登录用户与对方的关系标志（关注/粉丝/互关）。
     * 关注关系的 followerId / followeeId 在 user_follow 表中均为内部 id，故此处统一用内部 id 比对。
     * @param otherUser 目标用户实体
     * @return 他人视图 VO
     */
    private UserVO buildOtherUserVO(User otherUser) {
        // 防御：查不到用户（不存在/已删除）时直接抛「用户不存在」，避免后续 copyProperties 返回 null 后 setter 触发 NPE
        ThrowUtils.throwIf(BeanUtil.isEmpty(otherUser), ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        boolean login = StpUtil.isLogin();
        UserVO userVO = BeanUtil.copyProperties(otherUser, UserVO.class);
        userVO.setPhone("***");
        userVO.setEmail("***");
        if(login){
            long loginId = StpUtil.getLoginIdAsLong();
            long otherId = otherUser.getId();

            // 我是否关注了他
            LambdaQueryWrapper<UserFollow> iFollowHimWrapper = new LambdaQueryWrapper<>();
            iFollowHimWrapper.eq(UserFollow::getFollowerId, loginId)
                    .eq(UserFollow::getFolloweeId, otherId)
                    .eq(UserFollow::getStatus, 1);
            boolean isFollowed = userFollowMapper.exists(iFollowHimWrapper);
            userVO.setIsFollowed(isFollowed);

            // 他是否是我的粉丝（即他关注了我）
            LambdaQueryWrapper<UserFollow> heFollowMeWrapper = new LambdaQueryWrapper<>();
            heFollowMeWrapper.eq(UserFollow::getFollowerId, otherId)
                    .eq(UserFollow::getFolloweeId, loginId)
                    .eq(UserFollow::getStatus, 1);
            boolean isFans = userFollowMapper.exists(heFollowMeWrapper);
            userVO.setIsFans(isFans);

            // 互相关注 = 我关注他 且 他关注我
            userVO.setIsMutual(isFollowed && isFans);
        }
        return userVO;
    }

    /**
     * 基于 Redis 原子自增生成对外编码 userId。
     * <p>首次（Redis 计数器不存在）以「库内已有最大 userId」与默认基准 {@link #USER_ID_BASE} 取大作为起点，
     * 之后每次 {@code INCR} 保证全局唯一、单调递增；setIfAbsent 仅初始化一次，不会覆盖已自增的值。</p>
     *
     * @return 全新的 userId
     */
    private Long generateUserId() {
        // 仅当计数器尚未初始化时，才以库内最大 userId 为基准写入，避免覆盖已自增的计数
        if (!stringRedisTemplate.hasKey(USER_ID_COUNTER_KEY)) {
            //查询出最大的id
            List<Object> objects = baseMapper.selectObjs(new QueryWrapper<User>().select("max(userId)"));
            Object maxObj = objects.get(0);
            long dbMax = Objects.nonNull(maxObj) ? ((Number) maxObj).longValue() : 0L;

            long base = Math.max(dbMax, USER_ID_BASE);

            stringRedisTemplate.opsForValue().setIfAbsent(USER_ID_COUNTER_KEY, String.valueOf(base));
        }
        // 原子自增，返回自增后的值（首次为 base + 1）
        return stringRedisTemplate.opsForValue().increment(USER_ID_COUNTER_KEY);
    }
}