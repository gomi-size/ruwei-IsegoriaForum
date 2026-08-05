package com.ruwei.domain.vo;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 创建帖子结束后，展示的vo
 */
@Data
public class PostVO implements Serializable {
    /**
     * 
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 对外唯一编码
     */
    private String postCode;

    /**
     * 作者
     */
    private Long userId;

    /**
     * 所属板块
     */
    private Long boardId;

    /**
     * 标题
     */
    private String title;

    /**
     * 正文
     */
    private String content;

    /**
     * 封面图URL
     */
    private String cover;

    /**
     * 视频地址
     */
    private String videoUrl;

    /**
     * 话题（这里就是tag）
     */
    private String topic;

    /**
     * 1公开 2仅粉丝可见 3私密(仅作者)
     */
    private Integer visibility;

    /**
     * 生命周期状态: 1已发布 2草稿 3审核中 4下架 5删除
     */
    private Integer status;

    /**
     * 点赞数(热度公式×1)
     */
    private Integer likeCount;

    /**
     * 评论数(热度公式×2)
     */
    private Integer commentCount;

    /**
     * 收藏数(热度公式×3)
     */
    private Integer collectCount;

    /**
     * 浏览数(冷启动召回: viewCount<阈值)
     */
    private Integer viewCount;

    /**
     * 分享数(热度公式×4)
     */
    private Integer shareCount;

    /**
     * 热度分 
     */
    private BigDecimal score;

    /**
     * 置顶(重排强插第1/2位)
     */
    private Integer isTop;

    /**
     * 精华
     */
    private Integer isEssence;

    /**
     * 审核结果: 1待审 2通过 3驳回
     */
    private Integer auditStatus;

    /**
     * 纬度
     */
    private BigDecimal latitude;

    /**
     * 经度
     */
    private BigDecimal longitude;

    /**
     * 位置名称
     */
    private String locationName;

    /**
     * 图片URL列表（按 sort 排序）
     */
    private List<String> images;

    /**
     * 标签名列表
     */
    private List<String> tags;

    /**
     * 发布时间
     */
    private Date createdAt;

    /**
     * 
     */
    private Date updatedAt;

    /**
     * 逻辑删除: 0未删 1已删(与board表@TableLogic对齐)
     */
    @TableLogic
    private Integer isDelete;

    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}