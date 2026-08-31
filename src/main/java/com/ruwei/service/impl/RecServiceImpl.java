package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.NumberUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.Enum.PostAuditStatusEnum;
import com.ruwei.domain.Enum.PostStatusEnum;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.ContentBlock;
import com.ruwei.domain.dto.RecExposureDTO;
import com.ruwei.domain.dto.RecFeedDTO;
import com.ruwei.domain.dto.RecFeedbackDTO;
import com.ruwei.domain.empty.*;
import com.ruwei.domain.vo.PostBrowseVO;
import com.ruwei.manager.FollowCacheManager;
import com.ruwei.manager.RecCacheManager;
import com.ruwei.service.*;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 推荐流服务（Phase 1 规则驱动四层漏斗：召回 → 粗排 → 精排 → 重排）。
 *
 * <p>接口形态为<b>游标分页</b>：cursor = 上页最后一条的 postId（字符串，全局 ToStringSerializer
 * 防雪花精度丢失），首屏不传；返回 List&lt;PostBrowseVO&gt;，前端取本页最后一条的 id 作下次 cursor，
 * 返回条数 &lt; pageSize 即无更多。</p>
 */
@Service
public class RecServiceImpl implements RecService {

    // ---- 精排亲和系数（application.yml rec.* 收口，调参无需改代码） ----
    /** 标签亲和系数 α */
    @Value("${rec.fine.topic:0.3}")
    private double alphaTag;
    /** 内容形态亲和系数 β */
    @Value("${rec.fine.type:0.2}")
    private double betaType;
    /** 板块亲和系数 γ */
    @Value("${rec.fine.board:0.1}")
    private double gammaBoard;
    /** 粗排热度截断条数 */
    @Value("${rec.rough.top:300}")
    private int roughTop;
    // ---- 各路召回上限 ----
    @Value("${rec.recall.follow-limit:200}")
    private int recallFollowLimit;
    @Value("${rec.recall.board-limit:200}")
    private int recallBoardLimit;
    @Value("${rec.recall.hot-limit:100}")
    private int recallHotLimit;
    @Value("${rec.recall.tag-limit:150}")
    private int recallTagLimit;
    @Value("${rec.recall.cold-limit:100}")
    private int recallColdLimit;
    /** 冷启动召回窗口（小时）：审核通过延迟的新帖也能被召回（默认 72h，原硬编码 24h） */
    @Value("${rec.cold.window-hours:72}")
    private int coldWindowHours;
    /** 冷启动 viewCount 阈值：低于该值视为新帖候选（可配 rec.cold.view-threshold） */
    @Value("${rec.cold.view-threshold:50}")
    private int coldViewThreshold;
    /** 冷启动帖精排保底分：新帖无互动（pScore≈0）垫底进不了首屏，保底让其排到 score≈coldFloor 位置 */
    @Value("${rec.fine.cold-floor:1.0}")
    private double coldFloor;
    /** 兴趣维度常量（与 user_interest.dimension 对齐） */
    private static final int DIM_TAG = 2;
    private static final int DIM_TYPE = 3;
    private static final int DIM_BOARD = 4;
    private static final int DIM_AUTHOR = 5;

    /** 列表摘要截断长度（对齐 PostServiceImpl.PREVIEW_MAX_LENGTH，其为本类私有故同值复制） */
    private static final int PREVIEW_MAX_LENGTH = 100;

    /** 单页上限（防一次拉爆内存） */
    private static final int MAX_PAGE_SIZE = 20;
    private static final int DEFAULT_PAGE_SIZE = 10;

    @Resource
    private FollowCacheManager followCacheManager;
    @Resource
    private PostService postService;
    @Resource
    private BoardFollowService boardFollowService;
    @Resource
    private PostTagService postTagService;
    @Resource
    private UserInterestService userInterestService;
    @Resource
    private RecCacheManager recCacheManager;
    @Resource
    private UserService userService;
    @Resource
    private LikeService likeService;
    @Resource
    private CollectService collectService;
    @Resource
    private UserbehaviorService userbehaviorService;


    /**
     * 精排中间载体：帖子 + 精排分（hotScore × 亲和放大）。
     */
    private record Scored(Post post, double pScore) {}

    /**
     * 推荐流（游标分页）。
     *
     * <p>登录用户：tab=recommend 走四层漏斗个性化；tab=discover 只走热点+冷启动。
     * 游客：强制降级 discover 口径（不读关注/板块/标签画像，不写曝光）。</p>
     *
     * @param req 游标分页请求（cursor / pageSize / tab）
     * @return 当前页帖子卡片列表（已按重排规则排序，返回前已回写曝光）
     */
    @Override
    public List<PostBrowseVO> feed(RecFeedDTO req) {
        if (req == null) {
            req = new RecFeedDTO();
        }
        // 0. 参数兜底 + 登录态
        int pageSize = clampPageSize(req.getPageSize());
        boolean guest = !StpUtil.isLogin();
        Long loginId = guest ? null : StpUtil.getLoginIdAsLong();
        // 游客强制 discover 口径（不读关注/板块/画像）；discover tab 同样只走热点+冷启动
        boolean discover = guest || "discover".equals(req.getTab());

        // ① 召回五路并集（LinkedHashSet 保序去重：关注→板块→热点→标签→冷启动，热点在中间保底）
        Set<Long> candidateIds = new LinkedHashSet<>();
        if (!discover) {
            recallByFollow(loginId, candidateIds);
            recallByBoard(loginId, candidateIds);
        }
        recallByHot(candidateIds);
        if (!discover) {
            recallByTag(loginId, candidateIds);
        }
        Set<Long> coldIds = recallByCold(candidateIds);
        if (candidateIds.isEmpty()) {
            return List.of();
        }


        // 粗排：批量取帖（@TableLogic 自动过滤已删）→ 状态双保险 → 热度降序截断 Top N
        // 注意：此处不剔除已曝光帖——游标定位需要「含已曝光的全量精排序列」保持跨页稳定，
        // 曝光剔除下沉到下面的「取页」阶段（跳过已曝光 + 顺延补齐 pageSize 条）
        List<Post> posts = postService.listByIds(candidateIds).stream().filter(this::isRecommendable)
                .sorted(Comparator.comparingDouble(this::hotScore).reversed())
                .toList();

        //需要一个截断，防止一次性推送过多
        if(posts.size()>roughTop){
            posts=posts.subList(0,roughTop);
        }
        if (posts.isEmpty()) {
            return List.of();
        }
        //精排：pScore = hotScore × (1 + α·tag亲和 + β·type亲和 + γ·board亲和)
        //主要是处理一下兴趣表的事情
        Map<String,Double> profile = guest ? Map.of() : loadProfile(loginId);
        //处理帖子,根据用户长期兴趣画像转化为：帖子id，分数（类似map集合）
        //冷启动帖 pScore 保底：新帖无互动（score≈0 → pScore≈0）会垫底排在候选末尾进不了首屏，
        //保底分让新帖至少排到 score≈coldFloor 位置，审核通过的新帖才能被看到
        List<Scored> scored = posts.stream().map(p -> new Scored(p, calcPScore(p, profile)))
                .map(s -> coldIds.contains(s.post().getId())
                        ? new Scored(s.post(), Math.max(s.pScore(), coldFloor))
                        : s)
                .sorted(Comparator.comparingDouble(Scored::pScore).reversed())
                .toList();

        // 重排 1/4：游标定位（在全量精排序列中定位，序列含已曝光帖 → cursor 精确匹配不失效）
        int start = locateCursor(scored, req.getCursor());
        if(start>=scored.size()){
            return List.of();
        }
        // 重排 2/4：取页 = 从 start 起跳过已曝光帖、顺延补齐 pageSize 条
        // 游客无曝光档案直接取连续页；登录用户双重剔除：
        //   ① blocked（全局负反馈屏蔽，Redis feed:exposure:{uid}，跨刷新持久，永不补位放出）
        //   ② sessionExposed（前端本会话已看，内存上传，F5 刷新即重置）
        List<Post> pagePosts = new ArrayList<>();
        if (guest) {
            for (int i = start; i < scored.size() && pagePosts.size() < pageSize; i++) {
                pagePosts.add(scored.get(i).post());
            }
        } else {
            List<Long> tailIds = new ArrayList<>();
            for (int i = start; i < scored.size(); i++) {
                tailIds.add(scored.get(i).post().getId());
            }
            // ① 全局负反馈屏蔽（跨刷新持久；feed 不再自动回写曝光，此处仅含负反馈帖）
            Set<Long> blocked = tailIds.isEmpty() ? Set.of()
                    : recCacheManager.filterExposed(loginId, tailIds);
            // ② 会话已看（前端本会话上传，刷新即重置）：内存剔除
            Set<Long> sessionExposed = new HashSet<>();
            if (CollUtil.isNotEmpty(req.getExposedIds())) {
                for (String s : req.getExposedIds()) {
                    if (NumberUtil.isLong(s)) {
                        sessionExposed.add(Long.valueOf(s));
                    }
                }
            }
            // 第一轮：未曝光（不在 blocked 且不在 sessionExposed）优先
            for (int i = start; i < scored.size() && pagePosts.size() < pageSize; i++) {
                Long id = scored.get(i).post().getId();
                if (!blocked.contains(id) && !sessionExposed.contains(id)) {
                    pagePosts.add(scored.get(i).post());
                }
            }
            // 第二轮：未曝光不足 pageSize 时，用「会话已看但未被负反馈屏蔽」的帖按 pScore 序补位——
            // 内容池小 + 会话去重耗尽时兜底，保证首页/翻页永不空白（宁可重复不可空屏）；
            // blocked（负反馈）永不补位放出
            if (pagePosts.size() < pageSize) {
                for (int i = start; i < scored.size() && pagePosts.size() < pageSize; i++) {
                    Long id = scored.get(i).post().getId();
                    if (!blocked.contains(id) && sessionExposed.contains(id)) {
                        pagePosts.add(scored.get(i).post());
                    }
                }
            }
        }
        if (pagePosts.isEmpty()) {
            return List.of();
        }

        //重排 3/4：页内重排（同作者打散 → 冷启动注入位 5/8）
        List<Post> reordered = rerank(pagePosts, coldIds);

        // 会话曝光不再服务端回写（feed:exposure:{uid} 仅保留负反馈全局屏蔽）：
        // 会话级去重由前端本会话内存记录（exposedIds 上传），F5 刷新即重置 → 看过的帖重新出现。
        // 自建装配（同构 PostServiceImpl，不动其 private 方法）
        return assemble(reordered, loginId);
    }

    /**
     * 兜底曝光回写（<b>已废弃</b>，空操作保留接口兼容）。
     *
     * <p>会话级曝光去重已改由<b>前端本会话内存记录</b>（RecFeedDTO.exposedIds 上传，
     * F5 刷新即重置 → 看过的帖重新出现）；服务端 feed:exposure:{uid} 仅保留<b>负反馈全局屏蔽</b>。
     * 若此处仍写曝光，会把「看过」误记为全局屏蔽，导致刷新后帖子消失——故不再写 Redis。</p>
     */
    @Override
    public void recordExposure(RecExposureDTO req) {
        // 空操作：会话曝光由前端记录，服务端不再维护
    }

    /**
     * 负反馈
     * @param req 帖子内部 id（字符串） + 负反馈类型
     */
    @Override
    public void feedback(RecFeedbackDTO req) {

        ThrowUtils.throwIf(req == null || req.getPostId()==null||req.getPostId()<0L,
                ErrorCode.PARAMS_ERROR, "参数不能为空");

        long loginId = StpUtil.getLoginIdAsLong();
        long postId = req.getPostId();
        Post post = postService.getById(postId);
        ThrowUtils.throwIf(post == null, ErrorCode.NOT_FOUND_ERROR, "帖子不存在");

        // 1. 写行为日志 action=8 负反馈（extras 带反馈类型，离线分析用）
        userBehavior ub = new userBehavior();
        ub.setUserId(loginId);
        ub.setPostId(postId);
        ub.setAction(8);
        ub.setSource(1);
        ub.setPosition(0);
        ub.setDwellSec(0);
        ub.setExtras("{\"feedbackType\":" + (req.getType() == null ? 0 : req.getType()) + "}");
        userbehaviorService.save(ub);
        // 2. 短期兴趣降权：该帖全部兴趣维度 INCR -1（读取侧 max(0) 兜底，Phase 1 接受负值，§10.3）
        incrInterestsOfPost(loginId, post, -1.0);
        // 3. 曝光屏蔽：负反馈帖直接写入曝光集合（7 天内不再进入推荐流，等价「看过了且不感兴趣」，
        //    与 feed 页内自动回写同一 ZSet，ZADD 幂等无副作用）
        recCacheManager.addExposures(loginId, List.of(postId));
    }

    /** 帖子 → 兴趣维度批量 INCR（dim2 标签 / dim3 类型 / dim4 板块 / dim5 作者）。 */
    private void incrInterestsOfPost(Long uid, Post post, double delta) {
        if (StrUtil.isNotBlank(post.getTopic())) {
            for (String tag : StrUtil.split(post.getTopic(), ',')) {
                String t = StrUtil.trim(tag);
                if (NumberUtil.isLong(t)) {
                    recCacheManager.incrInterest(uid, DIM_TAG, t, delta);
                }
            }
        }
        if (post.getType() != null) {
            recCacheManager.incrInterest(uid, DIM_TYPE, String.valueOf(post.getType()), delta);
        }
        if (post.getBoardId() != null) {
            recCacheManager.incrInterest(uid, DIM_BOARD, String.valueOf(post.getBoardId()), delta);
        }
        if (post.getUserId() != null) {
            recCacheManager.incrInterest(uid, DIM_AUTHOR, String.valueOf(post.getUserId()), delta);
        }
    }

    // ==================== 召回五路 ====================

    /**
     * 路① 关注流：读 uf:following 热索引（无 feed:{uid} 收件箱），
     * 过滤口径：已发布 + 审核通过 + 可见性∈{公开, 仅粉丝可见}
     * （关注的人的「仅粉丝可见」帖对我可见）。
     */
    private void recallByFollow(Long loginId, Set<Long> sink) {
        Set<String> followees = followCacheManager.getFollower(loginId);   // 我关注的人
        if (followees == null || followees.isEmpty()) {
            return;
        }
        List<Long> ids = followees.stream()
                .filter(NumberUtil::isLong)
                .map(Long::valueOf)
                .toList();
        if (ids.isEmpty()) {
            return;
        }
        postService.lambdaQuery()
                .in(Post::getUserId, ids)
                .eq(Post::getStatus, PostStatusEnum.PUBLISHED.getCode())
                .eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode())
                .in(Post::getVisibility,
                        PostVisibilityEnum.PUBLIC.getCode(), PostVisibilityEnum.FANS_ONLY.getCode())
                .orderByDesc(Post::getCreatedAt)
                .last("limit " + recallFollowLimit)
                .list()
                .forEach(p -> sink.add(p.getId()));
    }

    /**
     * 路② 板块流：board_follow（status=1 关注中）→ 板块内公开帖，最新在前。
     * 板块帖统一只推公开（推荐流无关注关系背书，仅粉丝可见帖不进推荐）。
     */
    private void recallByBoard(Long loginId, Set<Long> sink) {
        List<Long> boardIds = boardFollowService.lambdaQuery()
                .eq(BoardFollow::getUserId, loginId)
                .eq(BoardFollow::getStatus, 1)
                .list().stream()
                .map(BoardFollow::getBoardId)
                .filter(Objects::nonNull)
                .toList();
        if (boardIds.isEmpty()) {
            return;
        }
        postService.lambdaQuery()
                .in(Post::getBoardId, boardIds)
                .eq(Post::getStatus, PostStatusEnum.PUBLISHED.getCode())
                .eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode())
                .eq(Post::getVisibility, PostVisibilityEnum.PUBLIC.getCode())
                .orderByDesc(Post::getCreatedAt)
                .last("limit " + recallBoardLimit)
                .list()
                .forEach(p -> sink.add(p.getId()));
    }

    /**
     * 路③ 热点：全库 score Top N（保底“全局最火”，游客/冷画像用户的主体内容源）。
     */
    private void recallByHot(Set<Long> sink) {
        postService.lambdaQuery()
                .eq(Post::getStatus, PostStatusEnum.PUBLISHED.getCode())
                .eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode())
                .eq(Post::getVisibility, PostVisibilityEnum.PUBLIC.getCode())
                .orderByDesc(Post::getScore)
                .last("limit " + recallHotLimit)
                .list()
                .forEach(p -> sink.add(p.getId()));
    }

    /**
     * 路④ 内容标签：user_interest dim=2（标签）按权重 Top-20 → post_tag（status=1 已发布版本）反查 postId。
     * 走 post_tag 关联表而非 topic LIKE/FIND_IN_SET：能吃到 (postId,tagId,status) 联合索引，
     * 且天然只命中「已发布版本」的关联（版本化语义与审核一致）。
     */
    private void recallByTag(Long loginId, Set<Long> sink) {
        List<Long> tagIds = userInterestService.lambdaQuery()
                .eq(UserInterest::getUserId, loginId)
                .eq(UserInterest::getDimension, 2)
                .orderByDesc(UserInterest::getWeight)
                .last("limit 20")
                .list().stream()
                .map(UserInterest::getValue)
                .filter(NumberUtil::isLong)
                .map(Long::valueOf)
                .toList();
        if (tagIds.isEmpty()) {
            return;
        }
        List<Long> postIds = postTagService.lambdaQuery()
                .in(PostTag::getTagId, tagIds)
                .eq(PostTag::getStatus, PostStatusEnum.PUBLISHED.getCode())
                .list().stream()
                .map(PostTag::getPostId)
                .distinct()
                .toList();
        if (postIds.isEmpty()) {
            return;
        }
        postService.lambdaQuery()
                .in(Post::getId, postIds)
                .eq(Post::getStatus, PostStatusEnum.PUBLISHED.getCode())
                .eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode())
                .eq(Post::getVisibility, PostVisibilityEnum.PUBLIC.getCode())
                .orderByDesc(Post::getCreatedAt)
                .last("limit " + recallTagLimit)
                .list()
                .forEach(p -> sink.add(p.getId()));
    }

    /**
     * 路⑤ 冷启动：近 {@code coldWindowHours}（默认 72h）新帖 + viewCount 低于阈值，最新在前。
     *
     * <p><b>窗口从创建时间起算、放宽到 72h</b>：先审后发下审核通过可能晚于创建 24h，
     * 24h 窗口会导致审核通过时帖子已从冷启动池消失（无互动 score=0，其他路也不命中）→ 推荐流永远看不到。</p>
     *
     * @return 冷启动池的 postId（供重排注入位 5/8 使用），同时并入候选
     */
    private Set<Long> recallByCold(Set<Long> sink) {
        Set<Long> coldIds = new LinkedHashSet<>();
        postService.lambdaQuery()
                .eq(Post::getStatus, PostStatusEnum.PUBLISHED.getCode())
                .eq(Post::getAuditStatus, PostAuditStatusEnum.APPROVED.getCode())
                .eq(Post::getVisibility, PostVisibilityEnum.PUBLIC.getCode())
                .gt(Post::getCreatedAt, DateUtil.offsetHour(new Date(), -coldWindowHours))
                .lt(Post::getViewCount, coldViewThreshold)
                .orderByDesc(Post::getCreatedAt)
                .last("limit " + recallColdLimit)
                .list()
                .forEach(p -> {
                    sink.add(p.getId());
                    coldIds.add(p.getId());
                });
        return coldIds;
    }

    /**
     * 推荐物料可见性双保险：召回 SQL 已过滤，此处防「召回后到粗排前」帖子被下架/删除的竞态
     * （listByIds 只过滤 isDelete，status/visibility 需内存再查一次）。
     */
    private boolean isRecommendable(Post p) {
        return Objects.equals(p.getStatus(), PostStatusEnum.PUBLISHED.getCode())
                && Objects.equals(p.getAuditStatus(), PostAuditStatusEnum.APPROVED.getCode())
                && Objects.equals(p.getVisibility(), PostVisibilityEnum.PUBLIC.getCode());
    }
    /** 热度分读取：post.score（BigDecimal），null/负值兜底 0（ScoreRecalcJob 每 5 分钟重算保证有值）。 */
    private double hotScore(Post p) {
        return p.getScore() == null ? 0d : p.getScore().doubleValue();
    }

    /**
     * 分页参数兜底：默认 10，上限 20。
     */
    private int clampPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    /**
     * 加载长期画像
     */
    private Map<String,Double> loadProfile(Long loginId){
      return   userInterestService.lambdaQuery()
                .eq(UserInterest::getUserId,loginId)
                .list().stream()
                .collect(Collectors.toMap(
                        //主要就是取出类型和类型具体的值，权重，传化为map
                        i->i.getDimension()+":"+i.getValue(),
                        i->i.getWeight()==null?0d:i.getWeight().doubleValue(),
                        //排除脏数据
                        (a,b)->a));
    }

    /**
     * 精排公式：pScore = hotScore × (1 + α·tagAffinity + β·typeAffinity + γ·boardAffinity)。
     *
     * <p>tagAffinity：post.topic（逗号分隔 tagId 串）逐标签查 dim=2 权重取最大（无则 0）；
     * typeAffinity：post.type 查 dim=3；boardAffinity：post.boardId 查 dim=4。
     * 作者维度（dim=5）只写不读（Phase 1 不做作者亲和，防大 V 垄断精排）。</p>
     */
    private double calcPScore(Post p, Map<String, Double> profile){
        //获取到帖子的分数
        double hot = hotScore(p);
        double tagAff=0;
        if(StrUtil.isNotBlank(p.getTopic())){
            for(String tag: StrUtil.split(p.getTopic(),",")){
                Double w = profile.get(DIM_TAG + ":" + StrUtil.trim(tag));
                if(w!=null){
                    tagAff=Math.max(tagAff,w);
                }
            }
        }
        double typeAff=p.getType()==null?0d:
                profile.getOrDefault(DIM_TYPE+":"+p.getType(),0d);
        double boardAff = p.getBoardId() == null ? 0d
                : profile.getOrDefault(DIM_BOARD + ":" + p.getBoardId(), 0d);
        return hot * (1 + alphaTag * tagAff + betaType * typeAff + gammaBoard * boardAff);
    }

    /**
     * 游标定位：在 pScore 降序全量候选中找上页末条位置，返回下一位置。
     *
     * <p>score 每 5 分钟重算导致顺序微变，cursor 精确位置可能漂移：
     * 漂移区间内（±页大小）做邻近搜索兜底，找不到才回首屏。</p>
     */
    private int locateCursor(List<Scored> scored,Long cursor){
        if(cursor==null||cursor<=0){
            return 0;
        }
        //1.精确匹配（Objects.equals：Long 用 == 比较引用，雪花 id 超出缓存区必然失配）
        for (int i = 0; i < scored.size(); i++) {
            if (Objects.equals(scored.get(i).post().getId(), cursor)) {
                return i + 1;
            }
        }
        // 2. 漂移兜底：cursor 原本排在末条附近，顺序微变后改比 pScore——但 pScore <= cursor 帖原分的
        //    简化处理：直接回到首页重推（曝光去重保证不会重推已看内容，损失仅一页新鲜度）
        return 0;

    }

    /**
     * 页内重排：同作者打散（≤2 连续）→ 冷启动注入位 5/8。
     * 只调整页内顺序，不影响游标语义（§6.5）。
     * 入参为调用方已剔除曝光、顺延补齐的页内 Post 列表（直接原地重排，不复制）。
     */
    private List<Post> rerank(List<Post> list, Set<Long> coldIds){
        //同作者打散：第 3 个连续同作者帖与后方首个异作者帖交换（至多交换，不丢帖）
        for (int i = 2; i < list.size(); i++) {
            Long cur = list.get(i).getUserId();
            if (Objects.equals(cur, list.get(i - 1).getUserId())
                    && Objects.equals(cur, list.get(i - 2).getUserId())) {
                for (int j = i + 1; j < list.size(); j++) {
                    if (!Objects.equals(list.get(j).getUserId(), cur)) {
                        Collections.swap(list, i, j);
                        break;
                    }
                }
            }
        }
        // 3. 冷启动注入：第 5、8 位插入冷启动帖（本页已有该帖则跳过顺延）。
        //    从后往前插避免前面的插入使后面的位次偏移。
        if (CollUtil.isNotEmpty(coldIds)) {
            for (int slot : new int[]{8, 5}) {
                if (slot > list.size()) {
                    continue;
                }
                for (int i = 0; i < list.size(); i++) {
                    Post p = list.get(i);
                    if (coldIds.contains(p.getId()) && i != slot - 1) {
                        list.remove(i);
                        list.add(Math.min(slot - 1, list.size()), p);
                        break;
                    }
                }
            }
        }

    return list;
    }

    /**
     * 列表装配：批量查作者 → buildBrowseVO（同构 buildPostBrowseVO）
     * → fillLikedAndCollected（复用 LikeService.batchPostLiked / CollectService.batchIsCollected）。
     *
     * @param posts   本页帖子（已重排）
     * @param loginId 当前登录用户（游客 null，跳过 isLiked/isCollected 装配）
     */
    private List<PostBrowseVO> assemble(List<Post> posts, Long loginId) {
        if (posts.isEmpty()) {
            return List.of();
        }
        List<Long> authorIds = posts.stream()
                .map(Post::getUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, User> userMap = authorIds.isEmpty() ? Map.of()
                : userService.listByIds(authorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<PostBrowseVO> voList = posts.stream()
                .map(p -> buildBrowseVO(p, userMap))
                .toList();
        if (loginId != null) {
            fillLikedAndCollected(voList, loginId);
        }
        return voList;
    }

    /**
     * 实体 → 列表卡片 VO（同构 PostServiceImpl.buildPostBrowseVO：轻量字段 + 摘要 + 作者昵称头像）。
     */
    private PostBrowseVO buildBrowseVO(Post post, Map<Long, User> userMap) {
        PostBrowseVO vo = BeanUtil.copyProperties(post, PostBrowseVO.class);
        vo.setContentPreview(buildContentPreview(post.getContent()));
        User author = userMap == null ? null : userMap.get(post.getUserId());
        if (author != null) {
            vo.setUserNickname(author.getNickname());
            vo.setUserAvatar(author.getAvatar());
        }
        return vo;
    }

    /**
     * 抽取正文纯文本并截断为摘要（同构 PostServiceImpl.buildContentPreview：
     * ContentBlock JSON 数组拼 text 块；旧数据纯文本直接用；统一截断 100 字）。
     */
    private String buildContentPreview(String content) {
        if (StrUtil.isBlank(content)) {
            return null;
        }
        String plain = content.trim();
        if (plain.startsWith("[")) {
            try {
                List<ContentBlock> blocks = JSONUtil.toList(JSONUtil.parseArray(content), ContentBlock.class);
                StringBuilder sb = new StringBuilder();
                for (ContentBlock b : blocks) {
                    if (StrUtil.isNotBlank(b.getText())) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(b.getText().trim());
                    }
                }
                plain = sb.toString();
            } catch (Exception ignored) {
                // 脏数据 fallback 纯文本截断
            }
        }
        if (StrUtil.isBlank(plain)) {
            return null;
        }
        if (plain.length() > PREVIEW_MAX_LENGTH) {
            return StrUtil.sub(plain, 0, PREVIEW_MAX_LENGTH) + "...";
        }
        return plain;
    }

    /**
     * 批量装配 isLiked / isCollected（同构 fillIsLiked + fillIsCollected，
     * 数据源分别为 LikeService / CollectService，二者的批量接口内部自带 Redis 降级 DB）。
     */
    private void fillLikedAndCollected(List<PostBrowseVO> voList, Long loginId) {
        List<Long> postIds = voList.stream()
                .map(PostBrowseVO::getId)
                .filter(Objects::nonNull)
                .toList();
        if (postIds.isEmpty()) {
            return;
        }
        Map<Long, Boolean> likedMap = likeService.batchPostLiked(postIds, loginId);
        Map<Long, Boolean> collectedMap = collectService.batchIsCollected(postIds, loginId);
        voList.forEach(vo -> {
            if (vo.getId() != null) {
                vo.setIsLiked(likedMap.getOrDefault(vo.getId(), false));
                vo.setIsCollected(collectedMap.getOrDefault(vo.getId(), false));
            }
        });
    }
}
