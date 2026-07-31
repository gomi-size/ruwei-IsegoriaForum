package com.ruwei.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户查询条件（入参 DTO）。
 *
 * <p>用于后台用户列表 / 搜索等场景，配合 {@code QueryWrapperUtils.getUserQueryWrapper} 使用。</p>
 *
 * <p>字段说明：</p>
 * <ul>
 *     <li>{@link #id}：内部主键（雪花 ASSIGN_ID），精确匹配</li>
 *     <li>{@link #userId}：对外展示的唯一编码，精确匹配</li>
 *     <li>{@link #username}：登录名（手机或邮箱），模糊匹配</li>
 *     <li>{@link #nickname}：昵称，模糊匹配</li>
 *     <li>{@link #sortField} / {@link #sortOrder}：排序字段与方向</li>
 *     <li>{@link #current} / {@link #pageSize}：分页参数，供 {@code new Page<>(current, pageSize)} 使用</li>
 * </ul>
 */
@Data
public class UserQueryDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 当前页码，从 1 开始，默认 1
     */
    private long current = 1;

    /**
     * 每页条数，默认 10
     */
    private long pageSize = 10;


    /**
     * 内部主键 id（雪花 ASSIGN_ID），精确匹配
     */
    private Long id;

    /**
     * 对外展示的唯一编码，精确匹配
     */
    private Long userId;

    /**
     * 登录名（手机或邮箱），模糊匹配
     */
    private String username;

    /**
     * 昵称，模糊匹配
     */
    private String nickname;

    /**
     * 排序字段（对应数据库驼峰列名，如 id / createdAt / fansCount / postCount），为空则不排序
     */
    private String sortField;

    /**
     * 排序方向：{@code ascend} = 升序，其它或空 = 降序
     */
    private String sortOrder;
}
