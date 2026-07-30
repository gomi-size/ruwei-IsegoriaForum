package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.empty.UserFollow;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.service.UserFollowService;
import com.ruwei.mapper.UserFollowMapper;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
* @author Administrator
* @description 针对表【user_follow(关注关系表)】的数据库操作Service实现
* @createDate 2026-07-29 17:56:04
*
* <p><b>ID 体系约定：</b>本表 followerId / followeeId 统一存储用户的<b>内部主键 id</b>
* （即 Sa-Token 的 loginId，雪花 ASSIGN_ID），<u>不再</u>使用对外编码 userId。
* 对外接口同时接受 id 或 userId 入参，先经 {@link #resolveTargetInternalId(Long, Long)} 解析为内部 id 再落库，
* 这样既能兼容前端两种传参，又能让后续 WS / MQ 推送直接按内部 id 路由，避免两套 ID 混用。</p>
*/
@Service
public class UserFollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow>
    implements UserFollowService{

    @Resource
    @Lazy
    private  UserService userService;

    /**
     * 将入参解析为目标用户的内部主键 id。
     * <ul>
     *   <li>id 非空 → 视为内部主键，直接校验存在性后返回；</li>
     *   <li>否则 userId 非空 → 按对外编码查表，返回其内部 id；</li>
     *   <li>两者皆空 → 参数错误。</li>
     * </ul>
     * @param id     内部主键（优先）
     * @param userId 对外编码
     * @return 目标用户的内部 id
     */
    private Long resolveTargetInternalId(Long id, Long userId) {
        if (id != null) {
            User u = userService.getById(id);
            ThrowUtils.throwIf(BeanUtil.isEmpty(u), ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            return id;
        }
        if (userId != null) {
            User u = userService.lambdaQuery().eq(User::getUserId, userId).one();
            ThrowUtils.throwIf(BeanUtil.isEmpty(u), ErrorCode.NOT_FOUND_ERROR, "用户不存在");
            return u.getId();
        }
        ThrowUtils.throwIf(true, ErrorCode.PARAMS_ERROR, "请传入 id 或 userId");
        return null;
    }

    /**
     * 关注用户（幂等：已关注时再次调用视为重复操作并提示）。
     * 库内统一以内部 id 记录关系。
     * @param id     对方内部主键（与 userId 二选一）
     * @param userId 对方对外编码（与 id 二选一）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(Long id, Long userId) {
        // 当前登录用户的内部 id（= Sa-Token loginId = user_follow 存储键）
        long loginId = StpUtil.getLoginIdAsLong();

        // 解析对方内部 id（入参 id / userId 二选一）
        Long targetId = resolveTargetInternalId(id, userId);

        // 不能关注自己
        ThrowUtils.throwIf(loginId == targetId, ErrorCode.OPERATION_ERROR, "无法关注自己");

        // 查已有记录（均以内部 id 为键）
        UserFollow one = lambdaQuery().eq(UserFollow::getFollowerId, loginId)
                .eq(UserFollow::getFolloweeId, targetId)
                .one();

        if (BeanUtil.isEmpty(one)) {
            // 新增关注
            UserFollow userFollow = new UserFollow();
            userFollow.setFollowerId(loginId);
            userFollow.setFolloweeId(targetId);
            userFollow.setStatus(1);
            userFollow.setCreatedAt(new Date());
            userFollow.setUpdatedAt(new Date());

            boolean save = save(userFollow);
            ThrowUtils.throwIf(!save, ErrorCode.OPERATION_ERROR, "关注失败");

            // 我的关注数 +1
            ThrowUtils.throwIf(!incrementCount(User::getId, loginId, "followCount", 1),
                    ErrorCode.OPERATION_ERROR, "关注失败");
            // 对方粉丝数 +1（按内部 id 匹配，避免内外 id 混用）
            incrementCount(User::getId, targetId, "fansCount", 1);

            // 互粉：若对方也已关注我，则我的粉丝数也 +1
            if (isMutualFollow(loginId, targetId)) {
                incrementCount(User::getId, loginId, "fansCount", 1);
            }
        } else if (one.getStatus() == 2) {
            // 曾关注但已取消，恢复关注
            boolean update = lambdaUpdate().eq(UserFollow::getFollowerId, loginId)
                    .eq(UserFollow::getFolloweeId, targetId)
                    .set(UserFollow::getStatus, 1)
                    .set(UserFollow::getUpdatedAt, new Date())
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "关注失败");

            ThrowUtils.throwIf(!incrementCount(User::getId, loginId, "followCount", 1),
                    ErrorCode.OPERATION_ERROR, "关注失败");
            incrementCount(User::getId, targetId, "fansCount", 1);

            if (isMutualFollow(loginId, targetId)) {
                incrementCount(User::getId, loginId, "fansCount", 1);
            }
        } else {
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "无法重复关注");
        }
    }

    /**
     * 取消关注用户。
     * @param id     对方内部主键（与 userId 二选一）
     * @param userId 对方对外编码（与 id 二选一）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelFollowUser(Long id, Long userId) {
        long loginId = StpUtil.getLoginIdAsLong();
        Long targetId = resolveTargetInternalId(id, userId);

        ThrowUtils.throwIf(loginId == targetId, ErrorCode.OPERATION_ERROR, "无法取消关注自己");

        UserFollow one = lambdaQuery().eq(UserFollow::getFollowerId, loginId)
                .eq(UserFollow::getFolloweeId, targetId)
                .one();
        ThrowUtils.throwIf(BeanUtil.isEmpty(one), ErrorCode.NOT_FOUND_ERROR, "没有关注信息，请刷新页面");

        if (one.getStatus() == 1) {
            boolean update = lambdaUpdate().eq(UserFollow::getFollowerId, loginId)
                    .eq(UserFollow::getFolloweeId, targetId)
                    .set(UserFollow::getStatus, 2)
                    .set(UserFollow::getUpdatedAt, new Date())
                    .update();
            ThrowUtils.throwIf(!update, ErrorCode.OPERATION_ERROR, "取消关注失败");

            // 我的关注数 -1
            ThrowUtils.throwIf(!incrementCount(User::getId, loginId, "followCount", -1),
                    ErrorCode.OPERATION_ERROR, "取消关注失败");
            // 对方粉丝数 -1
            incrementCount(User::getId, targetId, "fansCount", -1);

            // 互粉解除：若对方已关注我，则我的粉丝数也 -1
            if (isMutualFollow(loginId, targetId)) {
                incrementCount(User::getId, loginId, "fansCount", -1);
            }
        } else if (one.getStatus() == 2) {
            ThrowUtils.throwIf(true, ErrorCode.OPERATION_ERROR, "已经处于取消关注状态");
        }
    }

    /**
     * 判断 targetId 是否已关注 loginId（互粉检测，仅统计 status=1）。
     * 两者均为内部 id。
     * @param loginId   当前用户内部 id
     * @param targetId  对方内部 id
     * @return 是否互相关注
     */
    private boolean isMutualFollow(Long loginId, Long targetId) {
        return lambdaQuery()
                .eq(UserFollow::getFollowerId, targetId)
                .eq(UserFollow::getFolloweeId, loginId)
                .eq(UserFollow::getStatus, 1)
                .exists();
    }

    /**
     * 原子自增/自减用户计数（DB 层 SQL 自增减，避免并发读改写丢失）。
     * 统一以内部 id 为准，杜绝内外 id 混用。
     * @param idColumn 主键列（此处恒为 User::getId）
     * @param idValue  内部主键值
     * @param field    待增减的计数字段名（与数据库列一致）
     * @param delta    增量（正数加、负数减）
     * @return 是否更新成功
     */
    private boolean incrementCount(SFunction<User, ?> idColumn, Object idValue, String field, int delta) {
        LambdaUpdateWrapper<User> uw = new LambdaUpdateWrapper<>();
        uw.eq(idColumn, idValue)
          .setSql(field + " = " + field + (delta >= 0 ? " + " : " - ") + Math.abs(delta));
        return userService.update(uw);
    }

    /**
     * 获取我关注的用户列表。
     * 以当前用户内部 id 检索，渲染时再经内部 id 查出对方 VO。
     * @return 关注列表（UserVO）
     */
    @Override
    public List<UserVO> getFollowUserList() {
        long loginId = StpUtil.getLoginIdAsLong();

        List<UserFollow> userFollowList = lambdaQuery()
                .eq(UserFollow::getFollowerId, loginId)
                .eq(UserFollow::getStatus, 1)
                .list();

        List<UserVO> userVOList = new ArrayList<>();
        userFollowList.forEach(userFollow -> {
            Long followeeId = userFollow.getFolloweeId();
            UserVO userVO = userService.getOtherUserVOInfoById(followeeId);
            userVOList.add(userVO);
        });
        return userVOList;
    }

    /**
     * 获取我的粉丝列表。
     * @return 粉丝列表（UserVO）
     */
    @Override
    public List<UserVO> getFansUserList() {
        long loginId = StpUtil.getLoginIdAsLong();

        List<UserFollow> userFansList = lambdaQuery()
                .eq(UserFollow::getFolloweeId, loginId)
                .eq(UserFollow::getStatus, 1)
                .list();
        List<UserVO> userVOList = new ArrayList<>();
        userFansList.forEach(useFans -> {
            Long followerId = useFans.getFollowerId();
            UserVO userVO = userService.getOtherUserVOInfoById(followerId);
            userVOList.add(userVO);
        });
        return userVOList;
    }
}
