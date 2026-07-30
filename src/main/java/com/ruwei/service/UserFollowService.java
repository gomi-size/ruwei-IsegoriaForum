package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.empty.UserFollow;
import com.ruwei.domain.vo.UserVO;

import java.util.List;


/**
* @author Administrator
* @description 针对表【user_follow(关注关系表)】的数据库操作Service
* @createDate 2026-07-29 17:56:04
*/
public interface UserFollowService extends IService<UserFollow> {

    /**
     * 关注用户
     * @param userId 对方的userId
     */
    void followUser(Long userId);

    /**
     * 取消关注
     * @param userId 对方的userId
     */
    void cancelFollowUser(Long userId);

    /**
     * 获取关注列表
     * @return
     */
    List<UserVO> getFollowUserList();


    /**
     * 获取粉丝列表
     * @return
     */
    List<UserVO> getFansUserList();
}
