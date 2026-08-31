package com.ruwei.domain.dto;

import com.ruwei.domain.vo.PostBrowseVO;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 推荐流请求（游标分页）。
 *
 * <p>接口形态为游标分页：{@code cursor} = 上页最后一条的 postId（字符串，
 * 雪花 id 防前端精度丢失），首屏不传；返回条数 &lt; pageSize 即无更多。</p>
 */
@Data
public class RecFeedDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 上页最后一条 postId（字符串），首屏不传
     */
    private Long cursor;

    /**
     * 每页条数，默认 10，上限 20（Service 内 clamp）
     */
    private Integer pageSize = 10;

    /**
     * recommend 综合（四层漏斗）/ discover 热点+冷启动；默认 recommend
     */
    private String tab = "recommend";

    /**
     * 前端本会话已收到的帖子卡片列表（PostBrowseVO 轻量卡片 VO）。<b>当前策略预留字段</b>：
     * 服务端曝光去重由 Redis 曝光档案（feed:exposure:{uid}，7 天 TTL）负责，空页时直接返回
     * 空结果（hasMore=false）不做回显，已看内容由前端本地已加载列表自行保留展示；本字段仅
     * 随请求上传留存，后续若需「无新帖不空屏」服务端按卡片回显可启用。前端建议只传最近
     * 100 条，防请求体膨胀。
     */
    private List<PostBrowseVO> postBrowseVOList;
}
