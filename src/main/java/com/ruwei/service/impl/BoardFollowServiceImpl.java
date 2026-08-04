package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.component.notification.event.BoardFollowEvent;
import com.ruwei.domain.dto.BoardFollowPageDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.domain.empty.BoardFollow;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.utils.QueryWrapperUtils;
import com.ruwei.domain.vo.UserVO;
import com.ruwei.mapper.BoardFollowMapper;
import com.ruwei.service.BoardFollowService;
import com.ruwei.service.BoardService;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
* @author Administrator
* @description 针对表【board_follow(用户关注板块表)】的数据库操作Service实现
* @createDate 2026-08-04 14:11:16
*
* <p><b>ID 体系约定：</b>本表 userId 列统一存储当前登录用户的<b>内部主键 id</b>
* （= Sa-Token loginId = 雪花 ASSIGN_ID），boardId 为板块内部主键。
* 与 user_follow 的 followerId/followeeId 存内部 id 的约定保持一致，杜绝内外 id 混用。</p>
*
* <p><b>与 user_follow 的差异：</b>本表无 status 字段（无软取消状态），
* 取消关注 = 物理删除关注记录；关注自己的板块是允许的（吧主可关注自己的吧）。</p>
*/
@Service
public class BoardFollowServiceImpl extends ServiceImpl<BoardFollowMapper, BoardFollow>
    implements BoardFollowService{

    @Resource
    private BoardService boardService;

    @Resource
    private UserService userService;

    @Resource
    private ApplicationEventPublisher eventPublisher;

    /**
     * 关注板块（幂等：已关注时再次调用视为重复操作并提示）。
     * 板块关注数 +1（DB 层原子自增）。
     * @param boardId 板块内部主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void followBoard(Long boardId) {
        // 当前登录用户内部 id（= Sa-Token loginId = board_follow.userId 存储值）
        long loginId = StpUtil.getLoginIdAsLong();

        // 1. 校验板块存在（@TableLogic 自动过滤已逻辑删除的板块）
        ThrowUtils.throwIf(boardId == null, ErrorCode.PARAMS_ERROR, "板块 id 不能为空");
        Board board = boardService.getById(boardId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(board), ErrorCode.NOT_FOUND_ERROR, "板块不存在");

        // 2. 幂等：已关注则拒绝重复关注（与 user_follow「无法重复关注」对齐）
        boolean exists = lambdaQuery().eq(BoardFollow::getUserId, loginId)
                .eq(BoardFollow::getBoardId, boardId)
                .exists();
        ThrowUtils.throwIf(exists, ErrorCode.OPERATION_ERROR, "无法重复关注");

        // 3. 新增关注记录（本表无 status 字段，取消关注 = 物理删除记录）
        BoardFollow boardFollow = new BoardFollow();
        boardFollow.setUserId(loginId);
        boardFollow.setBoardId(boardId);
        boardFollow.setCreatedAt(new Date());
        boolean saved = save(boardFollow);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "关注失败");

        // 4. 板块关注数 +1
        ThrowUtils.throwIf(!incrementBoardFollowCount(boardId, 1), ErrorCode.OPERATION_ERROR, "关注失败");

        // 5. 发布板块关注事件（AFTER_COMMIT 后由 BoardFollowEventListener 通知吧主）
        eventPublisher.publishEvent(new BoardFollowEvent(this, loginId, boardId, board.getCreatorId()));
    }

    /**
     * 取消关注板块（物理删除关注记录，板块关注数 -1）。
     * @param boardId 板块内部主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelFollowBoard(Long boardId) {
        long loginId = StpUtil.getLoginIdAsLong();

        ThrowUtils.throwIf(boardId == null, ErrorCode.PARAMS_ERROR, "板块 id 不能为空");
        Board board = boardService.getById(boardId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(board), ErrorCode.NOT_FOUND_ERROR, "板块不存在");

        // 查已有关注记录（均以内部 id 为键）
        BoardFollow one = lambdaQuery().eq(BoardFollow::getUserId, loginId)
                .eq(BoardFollow::getBoardId, boardId)
                .one();
        ThrowUtils.throwIf(BeanUtil.isEmpty(one), ErrorCode.NOT_FOUND_ERROR, "没有关注信息，请刷新页面");

        // 物理删除（无 status 软删字段，与 user_follow 的 status=2 方案不同）
        boolean removed = removeById(one.getId());
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "取消关注失败");

        // 板块关注数 -1
        incrementBoardFollowCount(boardId, -1);
    }

    /**
     * 当前登录用户是否已关注该板块。
     * @param boardId 板块内部主键
     * @return 是否已关注
     */
    @Override
    public Boolean isFollowed(Long boardId) {
        ThrowUtils.throwIf(boardId == null, ErrorCode.PARAMS_ERROR, "板块 id 不能为空");
        long loginId = StpUtil.getLoginIdAsLong();
        return lambdaQuery().eq(BoardFollow::getUserId, loginId)
                .eq(BoardFollow::getBoardId, boardId)
                .exists();
    }

    /**
     * 当前登录用户关注的板块列表（分页，按关注时间倒序）。
     * <p>实现要点（对齐 {@code UserFollowServiceImpl.getFollowUserList}）：
     * <ol>
     *   <li>先在 {@code board_follow} 表上分页，total = 关注板块数，顺序按关注时间倒序；</li>
     *   <li>用本页的 {@code boardId} 集合去 {@code board} 表批量查详情（小集合）；</li>
     *   <li>复用关系页的 current/size/total 组装最终板块页，保证「关注顺序」不被打乱。</li>
     * </ol>
     * 全部使用内部 id。</p>
     *
     * @param dto 分页参数（current / pageSize）
     * @return 已关注板块的分页结果（Board）
     */
    @Override
    public IPage<Board> getFollowBoardList(BoardFollowPageDTO dto) {
        long loginId = StpUtil.getLoginIdAsLong();

        // 1) 在关注关系表上分页（按关注时间倒序），total 即关注板块数
        IPage<BoardFollow> followPage = this.page(
                new Page<>(dto.getCurrent(), dto.getPageSize()),
                QueryWrapperUtils.getBoardFollowQueryWrapper(loginId));

        // 2) 取出本页板块内部 id（已按 createdAt 倒序），批量查板块详情并保持关注顺序
        List<Long> boardIds = followPage.getRecords().stream()
                .map(BoardFollow::getBoardId)
                .toList();
        List<Board> boardList;
        if (boardIds.isEmpty()) {
            boardList = List.of();
        } else {
            Map<Long, Board> boardMap = boardService.listByIds(boardIds).stream()
                    .collect(Collectors.toMap(Board::getId, b -> b, (a, b) -> a));
            boardList = boardIds.stream()
                    .map(boardMap::get)
                    .filter(Objects::nonNull)
                    .toList();
        }

        // 3) 复用关系页的分页元数据，组装最终板块页
        IPage<Board> result = new Page<>(followPage.getCurrent(), followPage.getSize(), followPage.getTotal());
        result.setRecords(boardList);
        return result;
    }

    /**
     * 当前登录用户创建的板块的粉丝列表（分页，按关注时间倒序）。
     * <p>实现要点与 {@link #getFollowBoardList} 一致：先取我创建的板块 id 集合，
     * 再在 {@code board_follow} 表上按 {@code boardId IN (...) } 分页，
     * 用本页关注者内部 id（去重）批量查 User 组装 {@code UserVO}，最后复用关系页分页元数据。</p>
     *
     * @param dto 分页参数（current / pageSize）
     * @return 粉丝用户的分页结果（UserVO）
     */
    @Override
    public IPage<UserVO> getFansBoardList(BoardFollowPageDTO dto) {
        long loginId = StpUtil.getLoginIdAsLong();

        // 1) 我创建的板块内部 id 集合（@TableLogic 自动过滤已逻辑删除的板块）
        List<Long> myBoardIds = boardService.lambdaQuery()
                .eq(Board::getCreatorId, loginId)
                .list().stream()
                .map(Board::getId)
                .toList();
        if (myBoardIds.isEmpty()) {
            // 未创建任何板块 → 没有粉丝，返回空页
            return new Page<>(dto.getCurrent(), dto.getPageSize());
        }

        // 2) 分页查这些板块的关注记录（按关注时间倒序），total 即粉丝记录数
        IPage<BoardFollow> fansPage = this.page(
                new Page<>(dto.getCurrent(), dto.getPageSize()),
                QueryWrapperUtils.getBoardFansQueryWrapper(myBoardIds));

        // 3) 取本页关注者内部 id（一个用户可关注多个我的板块，故去重），批量查用户详情组装 VO
        List<Long> userIds = fansPage.getRecords().stream()
                .map(BoardFollow::getUserId)
                .distinct()
                .toList();
        List<UserVO> voList;
        if (userIds.isEmpty()) {
            voList = List.of();
        } else {
            Map<Long, User> userMap = userService.listByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
            voList = userIds.stream()
                    .map(userMap::get)
                    .filter(Objects::nonNull)
                    .map(u -> BeanUtil.copyProperties(u, UserVO.class))
                    .toList();
        }

        // 4) 复用关系页的分页元数据，组装最终 VO 页
        IPage<UserVO> result = new Page<>(fansPage.getCurrent(), fansPage.getSize(), fansPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    /**
     * 原子自增/自减板块关注数（DB 层 SQL 自增减，避免并发读改写丢失）。
     * @param boardId 板块内部主键
     * @param delta   增量（正数加、负数减）
     * @return 是否更新成功
     */
    private boolean incrementBoardFollowCount(Long boardId, int delta) {
        LambdaUpdateWrapper<Board> uw = new LambdaUpdateWrapper<>();
        uw.eq(Board::getId, boardId)
          .setSql("followCount = followCount " + (delta >= 0 ? "+ " : "- ") + Math.abs(delta));
        return boardService.update(uw);
    }
}
