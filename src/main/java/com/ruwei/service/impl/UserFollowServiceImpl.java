package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
*/
@Service
public class UserFollowServiceImpl extends ServiceImpl<UserFollowMapper, UserFollow>
    implements UserFollowService{

    @Resource
    @Lazy
    private  UserService userService;

    /**
     * 关注/取消关注用户
     * 第一次点击就是关注用户，如果再次点击就取消关注
     * @param userId 对方的userId（对外展示的唯一编码）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followUser(Long userId) {
        //根据sa-token获取到当前登录用户的id
        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);
        // 先判空，避免下方 user.getUserId() 触发 NPE
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR,"用户不存在，出现错误，请重新登录");
        ThrowUtils.throwIf(user.getUserId().toString().equals(userId.toString()),ErrorCode.OPERATION_ERROR,"无法关注自己");

        // 社交关系统一用「对外编码」作为键（与 user_follow 表、getOtherUserVOInfo 一致）
        Long myUserId = user.getUserId();

        //先查询是否有记录
        UserFollow one = lambdaQuery().eq(UserFollow::getFollowerId, myUserId)
                .eq(UserFollow::getFolloweeId, userId)
                .one();

        //没有记录新添加记录
        if(BeanUtil.isEmpty(one)){
            //关注
            UserFollow userFollow = new UserFollow();
            userFollow.setFollowerId(myUserId);
            userFollow.setFolloweeId(userId);
            userFollow.setStatus(1);
            userFollow.setCreatedAt(new Date());
            userFollow.setUpdatedAt(new Date());

            boolean save = save(userFollow);
            ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"关注失败");

            // 我的关注数加一
            ThrowUtils.throwIf(!incrementCount(User::getId, loginId, "followCount", 1),
                    ErrorCode.OPERATION_ERROR, "关注失败");
            // 关注成功，对方的粉丝数加一（按对外编码匹配，避免误用内部 id）
            incrementCount(User::getUserId, userId, "fansCount", 1);

            // 互粉：若对方也已关注我，则我的粉丝数也加一
            if (isMutualFollow(myUserId, userId)) {
                incrementCount(User::getId, loginId, "fansCount", 1);
            }
        } else if(one.getStatus()==2){
            //如果之前关注了，但是取消了现在修改为关注
            boolean update = lambdaUpdate().eq(UserFollow::getFollowerId, myUserId)
                    .eq(UserFollow::getFolloweeId, userId)
                    .set(UserFollow::getStatus, 1)
                    .set(UserFollow::getUpdatedAt, new Date())
                    .update();
            ThrowUtils.throwIf(!update,ErrorCode.OPERATION_ERROR,"关注失败");

            // 我的关注数加一
            ThrowUtils.throwIf(!incrementCount(User::getId, loginId, "followCount", 1),
                    ErrorCode.OPERATION_ERROR, "关注失败");
            // 对方粉丝数加一
            incrementCount(User::getUserId, userId, "fansCount", 1);

            // 互粉检测
            if (isMutualFollow(myUserId, userId)) {
                incrementCount(User::getId, loginId, "fansCount", 1);
            }
        } else  {
            ThrowUtils.throwIf(true,ErrorCode.OPERATION_ERROR,"无法重新关注");
        }


    }

    /**
     * 取消关注
     * @param userId 对方的userId（对外展示的唯一编码）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelFollowUser(Long userId) {
        //根据sa-token获取到当前登录用户的id
        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(user), ErrorCode.NOT_FOUND_ERROR,"用户不存在，出现错误，请重新登录");
        ThrowUtils.throwIf(user.getUserId().toString().equals(userId.toString()),ErrorCode.OPERATION_ERROR,"无法取消关注自己");

        Long myUserId = user.getUserId();

        //先查询是否有记录
        UserFollow one = lambdaQuery().eq(UserFollow::getFollowerId, myUserId)
                .eq(UserFollow::getFolloweeId, userId)
                .one();
        ThrowUtils.throwIf(BeanUtil.isEmpty(one),ErrorCode.NOT_FOUND_ERROR,"没有关注信息，请刷新页面");
        if(one.getStatus()==1){
            //如果之前关注了，现在修改为取消关注
            boolean update = lambdaUpdate().eq(UserFollow::getFollowerId, myUserId)
                    .eq(UserFollow::getFolloweeId, userId)
                    .set(UserFollow::getStatus, 2)
                    .set(UserFollow::getUpdatedAt, new Date())
                    .update();
            ThrowUtils.throwIf(!update,ErrorCode.OPERATION_ERROR,"取消关注失败");

            // 我的关注数减一
            ThrowUtils.throwIf(!incrementCount(User::getId, loginId, "followCount", -1),
                    ErrorCode.OPERATION_ERROR, "取消关注失败");
            // 对方粉丝数减一
            incrementCount(User::getUserId, userId, "fansCount", -1);

            // 互粉：若对方也关注了我，则我的粉丝数也减一
            if (isMutualFollow(myUserId, userId)) {
                incrementCount(User::getId, loginId, "fansCount", -1);
            }
        } else if (one.getStatus()==2) {
            ThrowUtils.throwIf(true,ErrorCode.OPERATION_ERROR,"已经是取消关注状，取消关注失败");
        }

    }

    /**
     * 判断 userId 是否已关注 myUserId（互粉检测，仅统计 status=1）
     * @param myUserId 当前用户对外编码（Long）
     * @param userId   对方对外编码（Long）
     * @return 是否互相关注
     */
    private boolean isMutualFollow(Long myUserId, Long userId) {
        return lambdaQuery()
                .eq(UserFollow::getFollowerId, userId)
                .eq(UserFollow::getFolloweeId, myUserId)
                .eq(UserFollow::getStatus, 1)
                .exists();
    }

    /**
     * 原子自增/自减用户计数（DB 层 SQL 自增减，避免并发读改写丢失）。
     * 注意：loginId 为内部 id，对方使用对外编码列 userId，二者不可混用。
     * @param idColumn 主键列（自己用 User::getId，对方用 User::getUserId）
     * @param idValue  主键值
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
     * 获取关注列表
     * @return
     */
    @Override
    public List<UserVO> getFollowUserList() {
        //根据sa-token获取到当前登录用户的id
        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);

        List<UserFollow> userFollowList = lambdaQuery()
                .eq(UserFollow::getFollowerId, user.getUserId())
                .eq(UserFollow::getStatus,1)
                .list();

        List<UserVO> userVOList=new ArrayList<>();
        userFollowList.forEach(userFollow -> {
            Long followeeId = userFollow.getFolloweeId();
            UserVO userVO = userService.getOtherUserVOInfo(followeeId);
            userVOList.add(userVO);
        });
        return userVOList;
    }

    /**
     * 查看粉丝列表
     * @return
     */
    @Override
    public List<UserVO> getFansUserList() {
        //根据sa-token获取到当前登录用户的id
        long loginId = StpUtil.getLoginIdAsLong();
        User user = userService.getById(loginId);

        List<UserFollow> userFansList = lambdaQuery()
                .eq(UserFollow::getFolloweeId, user.getUserId())
                .eq(UserFollow::getStatus,1)
                .list();
        List<UserVO> userVOList=new ArrayList<>();
        userFansList.forEach(useFans -> {
            Long followerId = useFans.getFollowerId();
            UserVO userVO = userService.getOtherUserVOInfo(followerId);
            userVOList.add(userVO);
        });
        return userVOList;
    }
}
