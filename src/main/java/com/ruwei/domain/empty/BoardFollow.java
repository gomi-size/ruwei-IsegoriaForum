package com.ruwei.domain.empty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;
import lombok.Data;

/**
 * 用户关注板块表
 * @TableName board_follow
 */
@TableName(value ="board_follow")
@Data
public class BoardFollow implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 
     */
    private Long userid;

    /**
     * 
     */
    private Long boardId;

    /**
     * 
     */
    private Date createdAt;

    @Serial
    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}