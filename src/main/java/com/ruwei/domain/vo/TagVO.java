package com.ruwei.domain.vo;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;

/**
 * 前端展示标签表
 */

@Data
public class TagVO implements Serializable {

    private Long id;

    /**
     * 标签名(唯一)
     */
    private String name;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}