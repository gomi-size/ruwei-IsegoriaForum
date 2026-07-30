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
     * <p>入参可传 id（内部主键）或 userId（对外编码），二选一；库内统一以内部 id 存储关系。</p>
     * @param id     对方内部主键（优先使用）
     * @param userId 对方对外编码（与 id 二选一）
     */
    void followUser(Long id, Long userId);

    /**
     * 取消关注
     * <p>入参可传 id（内部主键）或 userId（对外编码），二选一；库内统一以内部 id 存储关系。</p>
     * @param id     对方内部主键（优先使用）
     * @param userId 对方对外编码（与 id 二选一）
     */
    void cancelFollowUser(Long id, Long userId);

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
