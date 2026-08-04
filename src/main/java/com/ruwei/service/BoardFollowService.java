package com.ruwei.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.BoardFollowPageDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.domain.empty.BoardFollow;
import com.ruwei.domain.vo.UserVO;


/**
* @author Administrator
* @description 针对表【board_follow(用户关注板块表)】的数据库操作Service
* @createDate 2026-08-04 14:11:16
*
* <p><b>ID 体系约定：</b>本表 {@code userId} 列存储当前登录用户的<b>内部主键 id</b>
* （即 Sa-Token 的 loginId，雪花 ASSIGN_ID），与 user_follow / notification 等关系表
* 统一内部 id 的约定一致；{@code boardId} 为板块内部主键。对外入参仅需板块内部主键 boardId，
* 关注者一律取自登录态，不依赖前端传参。</p>
*/
public interface BoardFollowService extends IService<BoardFollow> {

    /**
     * 关注板块（幂等：已关注时再次调用视为重复操作并提示）。
     * 板块关注数 +1。
     * @param boardId 板块内部主键
     */
    void followBoard(Long boardId);

    /**
     * 取消关注板块（物理删除关注记录，板块关注数 -1）。
     * @param boardId 板块内部主键
     */
    void cancelFollowBoard(Long boardId);

    /**
     * 当前登录用户是否已关注该板块。
     * @param boardId 板块内部主键
     * @return 是否已关注
     */
    Boolean isFollowed(Long boardId);

    /**
     * 当前登录用户关注的板块列表（分页，按关注时间倒序）。
     * @param dto 分页参数（current / pageSize）
     * @return 已关注板块的分页结果（Board）
     */
    IPage<Board> getFollowBoardList(BoardFollowPageDTO dto);

    /**
     * 当前登录用户创建的板块的粉丝列表（分页，按关注时间倒序）。
     * @param dto 分页参数（current / pageSize）
     * @return 粉丝用户的分页结果（UserVO）
     */
    IPage<UserVO> getFansBoardList(BoardFollowPageDTO dto);
}
