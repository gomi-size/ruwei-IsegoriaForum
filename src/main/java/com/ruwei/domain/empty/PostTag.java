package com.ruwei.domain.empty;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 帖子标签关联表
 * @TableName post_tag
 */
@TableName(value ="post_tag")
@Data
public class PostTag implements Serializable {
    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 帖子内部id
     */
    private Long postId;

    /**
     * 标签内部id
     */
    private Long tagId;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}