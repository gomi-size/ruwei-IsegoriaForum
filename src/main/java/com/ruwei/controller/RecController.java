package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaIgnore;
import com.ruwei.annotation.RateLimit;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.dto.RecExposureDTO;
import com.ruwei.domain.dto.RecFeedbackDTO;
import com.ruwei.domain.dto.RecFeedDTO;
import com.ruwei.domain.vo.PostBrowseVO;
import com.ruwei.service.RecService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 推荐流接口（Phase 1 规则驱动四层漏斗）。
 *
 * <p>鉴权口径：feed 整体 {@code @SaIgnore}（方法级注解就近覆盖类级 @SaCheckLogin，
 * 与 PostController.listPosts 同模式）——游客降级 discover 口径（热点+冷启动），
 * 登录用户按 tab 个性化；recordExposure / feedback 必须登录（类级 @SaCheckLogin 生效）。</p>
 */
@RestController
@RequestMapping("/recommend")
@SaCheckLogin
public class RecController {

    @Resource
    private RecService recService;

    /**
     * 推荐流（游标分页）。
     *
     * <p>请求体：{cursor（上页最后一条 postId，首屏不传）, pageSize（默认 10，上限 20）,
     * tab（recommend 综合 / discover 热点+冷启动）}。返回 List&lt;PostBrowseVO&gt;，
     * 前端取本页最后一条的 id 作下次 cursor；返回条数 &lt; pageSize 即无更多。</p>
     *
     * <p>游客可访问（降级 discover：仅热点+冷启动，不写曝光档案）；7 天内曝光去重仅推荐流生效。</p>
     */
    @PostMapping("/feed")
    @SaIgnore
    @RateLimit(limit = 30, window = 60, prefix = "rec")
    public BaseResponse<List<PostBrowseVO>> feed(@RequestBody(required = false) RecFeedDTO req) {
        return ResultUtils.success(recService.feed(req));
    }

    /**
     * 兜底曝光回写：feed 返回时已自动写曝光 ZSet；前端滚动渲染/弱网重试时可再调本接口防丢
     * （幂等：ZADD 同 member 只刷新时间戳，不产生重复记录）。
     */
    @PostMapping("/recordExposure")
    @RateLimit(limit = 10, window = 60, prefix = "rec")
    public BaseResponse<String> recordExposure(@RequestBody RecExposureDTO req) {
        recService.recordExposure(req);
        return ResultUtils.success("ok");
    }

    /**
     * 负反馈（「不感兴趣」等）：写 userBehavior(action=8) + 该帖全部兴趣维度短期降权（INCR 负值）。
     */
    @PostMapping("/feedback")
    @RateLimit(limit = 10, window = 60, prefix = "rec")
    public BaseResponse<String> feedback(@RequestBody RecFeedbackDTO req) {
        recService.feedback(req);
        return ResultUtils.success("ok");
    }
}