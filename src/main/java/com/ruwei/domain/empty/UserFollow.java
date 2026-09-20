package com.ruwei.domain.empty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 关注关系表
 *
 * <p>主键策略说明：本项目全局采用「内部雪花 id + 对外 userId」双主键体系，
 * 本表的 {@code id} 为纯技术主键（不对外暴露，对外只暴露 followerId / followeeId），
 * 因此与 User / Post / Comment / BoardFollow 等实体保持一致，统一使用
 * {@link IdType#ASSIGN_ID} 由 MyBatis-Plus 在插入前生成雪花 id。</p>
 *
 * <p><b>注意</b>：切勿改回 {@link IdType#AUTO}。该策略要求数据库列具备
 * AUTO_INCREMENT，一旦实际库的 {@code id} 列为 {@code NOT NULL} 但未设自增，
 * 插入时将抛出 {@code Field 'id' doesn't have a default value}。</p>
 *
 * @TableName userFollow
 */
@TableName(value ="user_follow")
@Data
public class UserFollow {
    /**
     * 主键（雪花 id，由 MyBatis-Plus ASSIGN_ID 策略生成）
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 主动关注者
     */
    private Long followerId;

    /**
     * 被关注者
     */
    private Long followeeId;

    /**
     * 1关注 2已取消关注
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createdAt;

    /**
     * 修改时间
     */
    private Date updatedAt;
}