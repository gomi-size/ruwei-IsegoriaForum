package com.ruwei.domain.empty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 通知表(历史存储/消息中心真相源)
 * @TableName notification
 */
@TableName(value ="notification")
@Data
public class Notification implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收者
     */
    private Long receiverId;

    /**
     * 触发者
     */
    private Long senderId;

    /**
     * 1点赞 2评论 3回复 4关注 5@提及 6系统 7收藏
     */
    private Integer type;

    /**
     * 1帖子 2评论
     */
    private Integer targetType;

    /**
     * 
     */
    private Long targetId;

    /**
     * 预览文案
     */
    private String content;

    /**
     * 业务幂等键(如 like:{uid}:{postId}), 防重复通知
     */
    private String bizKey;

    /**
     * 
     */
    private Integer isRead;

    /**
     * 
     */
    private Date createdAt;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}