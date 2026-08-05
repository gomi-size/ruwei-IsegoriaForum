package com.ruwei.domain.dto;

import com.baomidou.mybatisplus.annotation.*;
import com.ruwei.domain.empty.Tag;
import com.ruwei.domain.vo.TagVO;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 创建帖子的请求类
 *
 * <p>作者（userId）不由此 DTO 传入，统一取当前登录态内部 id（= Sa-Token loginId），
 * 防止前端伪造作者。</p>
 */
@Data
public class PostDTO implements Serializable {

    /**
     * 帖子内部主键（编辑时必传；创建时忽略）
     */
    private Long id;

    /**
     * 所属板块（可为空）
     */
    private Long boardId;

    /**
     * 标题（不能为空）
     */
    private String title;

    /**
     * 正文(发布时过敏感词 filter)（）
     */
    private String content;

    /**
     * 封面图URL（不能为空）
     */
    private String cover;

    /**
     * 1图文 2视频 3纯文
     */
    private Integer type;

    /**
     * 视频地址
     */
    private String videoUrl;

    /**
     * 话题（这里就是tag）
     */
    private List<TagVO> topic;

    /**
     * 1公开 2仅粉丝可见 3私密(仅作者)
     */
    private Integer visibility;

    /**
     * 生命周期状态: 1已发布 2草稿 3审核中 4下架 5删除
     */
    private Integer status;

    /**
     * 审核结果: 1待审 2通过 3驳回
     */
    private Integer auditStatus;

    /**
     * 置顶(重排强插第1/2位)
     */
    private Integer isTop;

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
     * 图片URL列表（图集，按顺序展示；全量替换语义：编辑时以本次列表为准）
     */
    private List<String> images;

    /**
     * 标签名列表（不存在则自动创建并 useCount+1）
     */
    private List<String> tags;


    @TableField(exist = false)
    private static final long serialVersionUID = 1L;
}