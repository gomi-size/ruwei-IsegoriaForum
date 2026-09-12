package com.ruwei.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;

/**
 * 前端展示标签表类
 *
 * <p>详情 {@code PostVO.topic} 与标签榜（{@code /tag/hot}、{@code /tag/list}）共用本 VO；
 * 卡片场景的精简结构见 {@link PostBrowseVO.TagBrief}（只带 id + name）。</p>
 */

@Data
public class TagVO implements Serializable {

    /**
     * 标签主键（雪花 id，<b>JSON 序列化为字符串</b>，防前端 2^53 精度丢失；
     * 详情页话题 chip 需用此 id 拼 {@code /tag/{id}} 跳转）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 标签名(唯一)
     */
    private String name;

    /**
     * 使用次数（热门标签榜排序依据）
     */
    private Integer useCount;

    /**
     * 1正常 2禁用
     */
    private Integer status;


    private static final long serialVersionUID = 1L;
}