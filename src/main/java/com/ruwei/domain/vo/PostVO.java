package com.ruwei.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 帖子对外展示 VO（创建 / 详情 / 列表复用）。
 *
 * <p><b>与实体的三点差异：</b></p>
 * <ul>
 *   <li><b>枚举字段回显文字</b>：visibility / status / auditStatus 均为中文文字
 *       （如 "公开" / "审核中" / "待审"），与 PostDTO 入参保持同一套词汇，前端无需再维护映射表；</li>
 *   <li><b>结构化字段</b>：topic 由 post.topic（逗号分隔的 tag id 串）解析为 List；
 *       imageUrl 由 post_image 表按 sort 升序装配；</li>
 *   <li><b>雪花 id 序列化为字符串</b>：id / userId / boardId 为 19 位雪花 id，超出 JS
 *       Number.MAX_SAFE_INTEGER（2^53-1），直接以数字输出前端会丢精度，故统一转字符串。</li>
 * </ul>
 *
 * <p>不含 isDelete（逻辑删除是持久层实现细节，不对外暴露）。</p>
 */
@Data
public class PostVO implements Serializable {

    /** 帖子内部主键（雪花 id，JSON 输出为字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 对外唯一编码
     */
    private String postCode;

    /**
     * 作者（雪花 id，JSON 输出为字符串）
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;

    /**
     * 所属板块（雪花 id，JSON 输出为字符串）
     */
    @JsonSerialize(using = ToStringSerializer.class)
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
     * 内容形态: 1图文 2视频 3纯文
     */
    private Integer type;

    /**
     * 视频地址
     */
    private String videoUrl;

    /**
     * 图片地址集合（来自 post_image 表，按 sort 升序）
     */
    private List<String> imageUrl;

    /**
     * 话题（tag 列表，由 post.topic 逗号串解析为 List&lt;TagVO&gt;，含 id+name）
     */
    private List<TagVO> topic;

    /**
     * 可见性文字: 公开 / 仅粉丝可见 / 私密
     * （对应 {@code PostVisibilityEnum}）
     */
    private String visibility;

    /**
     * 生命周期状态文字: 已发布 / 草稿 / 审核中 / 下架
     * （对应 {@code PostStatusEnum}）
     */
    private String status;

    /**
     * 审核结果文字: 待审 / 通过 / 驳回
     * （对应 {@code PostAuditStatusEnum}）
     */
    private String auditStatus;

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
     * 发布时间
     */
    private Date createdAt;

    /**
     * 最后更新时间
     */
    private Date updatedAt;

    private static final long serialVersionUID = 1L;
}
