package com.ruwei.domain.dto;

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
     * 本会话已曝光/已看的帖子内部 id 列表（前端内存记录，F5 刷新即清空 → 帖子重新出现）。
     *
     * <p><b>会话级去重</b>：服务端取页时剔除这些 id 并顺延补齐（保证 pageSize 与游标连续性）；
     * 负反馈「不感兴趣」由服务端全局屏蔽（feed:exposure:{uid}）负责，不在此列表，
     * 前端切勿把负反馈帖传回本字段（全局屏蔽优先，补位也不会放出）。游客忽略本字段。</p>
     */
    private List<String> exposedIds;
}
