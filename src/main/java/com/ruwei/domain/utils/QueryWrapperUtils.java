package com.ruwei.domain.utils;

import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ruwei.common.BusinessException;
import com.ruwei.common.ErrorCode;
import com.ruwei.domain.dto.BoardQueryDTO;
import com.ruwei.domain.dto.NotificationQueryDTO;
import com.ruwei.domain.dto.UserQueryDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.domain.empty.Notification;
import com.ruwei.domain.empty.User;
import com.ruwei.domain.empty.UserFollow;

import java.util.List;
import java.util.Set;

public class QueryWrapperUtils {

    /**
     * 允许参与排序的字段白名单（防止 orderBy 注入任意列名）。
     * 与 user 表驼峰列名保持一致。
     */
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of(
            "id", "userId", "createdAt", "updatedAt",
            "fansCount", "followCount", "postCount", "level", "exp", "status"
    );

    /**
     * 根据查询条件构造用户表的 QueryWrapper。
     *
     * <p>精确匹配：内部 {@code id}、对外 {@code userId}；模糊匹配：{@code username}、{@code nickname}。</p>
     *
     * @param userQueryDTO 查询条件，为 null 时抛参数异常
     * @return 已拼好条件的 QueryWrapper
     */
    public static QueryWrapper<User> getUserQueryWrapper(UserQueryDTO userQueryDTO) {
        if (userQueryDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryDTO.getId();
        Long userId = userQueryDTO.getUserId();
        String username = userQueryDTO.getUsername();
        String nickname = userQueryDTO.getNickname();
        String sortField = userQueryDTO.getSortField();
        String sortOrder = userQueryDTO.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // 精确匹配（列名与 user 表驼峰列一致）
        queryWrapper.eq(ObjUtil.isNotNull(id), "id", id);
        queryWrapper.eq(ObjUtil.isNotNull(userId), "userId", userId);
        // 模糊匹配
        queryWrapper.like(StrUtil.isNotBlank(username), "username", username);
        queryWrapper.like(StrUtil.isNotBlank(nickname), "nickname", nickname);

        // 排序：常量在前比较，避免 sortOrder 为 null 时空指针；列名走白名单防注入
        boolean isAsc = "ascend".equals(sortOrder);
        boolean canSort = StrUtil.isNotBlank(sortField) && ALLOWED_SORT_FIELDS.contains(sortField);
        queryWrapper.orderBy(canSort, isAsc, sortField);

        return queryWrapper;
    }

    /**
     * 「我关注的人」关系查询条件，用于分页 {@code userFollow} 表。
     *
     * <p>条件：{@code followerId = 当前用户内部 id} 且 {@code status = 1}（关注中），按 {@code createdAt} 倒序。</p>
     *
     * @param followerId 主动关注方内部 id（即 Sa-Token loginId）
     * @return 已拼好条件的 QueryWrapper（作用在 userFollow 表）
     */
    public static QueryWrapper<UserFollow> getFollowingQueryWrapper(Long followerId) {
        if (followerId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "followerId 不能为空");
        }
        QueryWrapper<UserFollow> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followerId", followerId)
                .eq("status", 1)
                .orderByDesc("createdAt");
        return queryWrapper;
    }

    /**
     * 「我的粉丝」关系查询条件，用于分页 {@code userFollow} 表。
     *
     * <p>条件：{@code followeeId = 当前用户内部 id} 且 {@code status = 1}（关注中），按 {@code createdAt} 倒序。</p>
     *
     * @param followeeId 被关注方内部 id（即 Sa-Token loginId）
     * @return 已拼好条件的 QueryWrapper（作用在 userFollow 表）
     */
    public static QueryWrapper<UserFollow> getFansQueryWrapper(Long followeeId) {
        if (followeeId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "followeeId 不能为空");
        }
        QueryWrapper<UserFollow> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("followeeId", followeeId)
                .eq("status", 1)
                .orderByDesc("createdAt");
        return queryWrapper;
    }

    /**
     * 根据内部 id 集合查询用户，可叠加 DTO 里的 username / nickname 模糊搜索与排序。

     * @param ids          目标用户内部 id 集合（来自关系表的对端 id）
     * @return 已拼好 {@code in("id", ids)} 及模糊/排序条件的 QueryWrapper
     */
    public static QueryWrapper<User> getUserInIdsQueryWrapper(UserQueryDTO userQueryDTO, List<Long> ids) {
        if (userQueryDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        // id 集合为空时直接给一个永假条件，避免 IN () 非法 SQL 或误查全表
        if (ids == null || ids.isEmpty()) {
            queryWrapper.eq("1", "0");
            return queryWrapper;
        }
        String username = userQueryDTO.getUsername();
        String nickname = userQueryDTO.getNickname();
        String sortField = userQueryDTO.getSortField();
        String sortOrder = userQueryDTO.getSortOrder();

        queryWrapper.in("id", ids);
        queryWrapper.like(StrUtil.isNotBlank(username), "username", username);
        queryWrapper.like(StrUtil.isNotBlank(nickname), "nickname", nickname);

        boolean isAsc = "ascend".equals(sortOrder);
        boolean canSort = StrUtil.isNotBlank(sortField) && ALLOWED_SORT_FIELDS.contains(sortField);
        queryWrapper.orderBy(canSort, isAsc, sortField);
        return queryWrapper;
    }

    /**
     * 通知（消息中心）分页查询条件。
     *
     * <p>条件：{@code receiverId = 当前用户内部 id}（仅查自己的通知），可选按 {@code type} 精确过滤；
     * 固定按 {@code createdAt} 时间<b>降序</b>（最新通知排在最前）。</p>
     *
     * @param receiverId 接收者内部 id（即当前登录用户 Sa-Token loginId）
     * @param dto        查询条件（current / pageSize / type）
     * @return 已拼好条件的 QueryWrapper（作用在 notification 表）
     */
    public static QueryWrapper<Notification> getNotificationQueryWrapper(Long receiverId, NotificationQueryDTO dto) {
        if (dto == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        if (receiverId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "receiverId 不能为空");
        }
        QueryWrapper<Notification> queryWrapper = new QueryWrapper<>();
        // 只查当前用户的通知
        queryWrapper.eq("receiverId", receiverId);
        // 按类型过滤：type 为 null 时不过滤，查全部类型
        queryWrapper.eq(dto.getType()!=0, "type", dto.getType());
        // 按时间降序，最新在前
        queryWrapper.orderByDesc("createdAt");
        return queryWrapper;
    }

    /**
     * 允许参与排序的字段白名单（防止 orderBy 注入任意列名）。
     * 与 board 表驼峰列名保持一致。
     */
    private static final Set<String> BOARD_ALLOWED_SORT_FIELDS = Set.of(
            "id", "name", "slug", "followCount", "postCount", "createdAt", "updatedAt", "status"
    );

    /**
     * 根据查询条件构造板块表的 QueryWrapper。
     *
     * <p>模糊匹配：{@code name}；精确匹配：{@code slug} / {@code creatorId}；
     * 可选按 {@code sortField} / {@code sortOrder} 排序；默认按 {@code followCount DESC}（关注数倒序）。</p>
     *
     * @param boardQueryDTO 查询条件，为 null 时抛参数异常
     * @return 已拼好条件的 QueryWrapper（作用在 board 表）
     */
    public static QueryWrapper<Board> getBoardQueryWrapper(BoardQueryDTO boardQueryDTO) {
        if (boardQueryDTO == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String name = boardQueryDTO.getName();
        String slug = boardQueryDTO.getSlug();
        String description = boardQueryDTO.getDescription();
        Long creatorId = boardQueryDTO.getCreatorId();
        String sortField = boardQueryDTO.getSortField();
        String sortOrder = boardQueryDTO.getSortOrder();

        QueryWrapper<Board> queryWrapper = new QueryWrapper<>();
        // 模糊匹配
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(description), "description", description);
        queryWrapper.like(ObjUtil.isNotNull(slug), "slug", slug);
        queryWrapper.like(ObjUtil.isNotNull(creatorId), "creatorId", creatorId);

        // 排序：常量在前比较，避免 sortOrder 为 null 时空指针；列名走白名单防注入
        boolean isAsc = "ascend".equals(sortOrder);
        boolean canSort = StrUtil.isNotBlank(sortField) && BOARD_ALLOWED_SORT_FIELDS.contains(sortField);
        if (canSort) {
            queryWrapper.orderBy( true,isAsc, sortField);
        } else {
            // 默认按关注数倒序（热门板块优先）
            queryWrapper.orderByDesc("followCount");
        }
        return queryWrapper;
    }
}
