package com.ruwei.component;

import cn.hutool.core.convert.Convert;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.WordTree;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.empty.SensitiveWord;
import com.ruwei.mapper.SensitiveWordMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 敏感词过滤器（内存 DFA 实现 - 多维防护增强版）。
 *
 * <p>启动时及管理端增删词后从数据库加载全量词，按 action 拆分为三棵 Hutool WordTree。
 * 引入双重文本检测机制（基础归一化 + 纯中文提取），有效抵御“澳s门s赌s场”等变种插字绕过。</p>
 */
@Slf4j
@Component
public class SensitiveWordFilter {

    /** 处置动作：替换成 *** 后发布 */
    public static final int ACTION_REPLACE = 1;
    /** 处置动作：直接拦截（拒绝发布） */
    public static final int ACTION_INTERCEPT = 2;
    /** 处置动作：进入审核队列，由调用方联动内容状态机 */
    public static final int ACTION_REVIEW = 3;

    @Resource
    private SensitiveWordMapper sensitiveWordMapper;

    /** 替换树：命中后替换为 *** */
    private volatile WordTree replaceTree = new WordTree();
    /** 拦截树：命中后直接拒绝 */
    private volatile WordTree interceptTree = new WordTree();
    /** 审核树：命中后转为送审 */
    private volatile WordTree reviewTree = new WordTree();

    /**
     * 容器启动后由 Spring 调用，首次把敏感词加载进内存 Trie。
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 从数据库整表加载敏感词，按 {@code action} 拆成三棵 Trie。
     *
     * <p>通常在启动及管理端增删词后调用：先新建三棵临时树并填充，再原子替换成员变量，
     * 保证刷新过程中正在处理的请求仍使用旧树、不受影响。加载失败时仅告警并保留旧树。</p>
     */
    public void refresh() {
        List<SensitiveWord> all;
        try {
            all = sensitiveWordMapper.selectList(null);
        } catch (Exception e) {
            log.warn("敏感词表加载失败，过滤器暂为空：{}", e.getMessage());
            return;
        }
        WordTree r = new WordTree();
        WordTree i = new WordTree();
        WordTree v = new WordTree();
        for (SensitiveWord sw : all) {
            String w = StrUtil.trimToNull(sw.getWord());
            if (w == null) {
                continue;
            }
            String key = normalize(w);
            if (StrUtil.isBlank(key)) {
                continue;
            }
            int action = sw.getAction() == null ? ACTION_REPLACE : sw.getAction();
            switch (action) {
                case ACTION_INTERCEPT -> i.addWord(key);
                case ACTION_REVIEW -> v.addWord(key);
                default -> r.addWord(key);
            }
        }
        this.replaceTree = r;
        this.interceptTree = i;
        this.reviewTree = v;
        log.info("敏感词加载完成，共 {} 条（替换/拦截/审核 已按 action 拆分）", all.size());
    }

    /**
     * 严格检查（用于用户资料等无审核流场景）。
     *
     * <p>采用<b>双重检测</b>：先对原文做基础归一化，再提取纯中文（剥离插入的字母/数字/符号），
     * 两个维度的文本只要命中任一棵树（替换 / 拦截 / 审核）即视为包含敏感或违规内容，
     * 抛出 {@code PARAMS_ERROR} 并附带字段名提示。空白文本直接放行（不做检查）。</p>
     *
     * @param text 待检查文本（如昵称、个性签名、所在地）
     * @param fieldName 字段中文名，用于异常提示，例如“昵称”
     */
    public void checkStrict(String text, String fieldName) {
        if (StrUtil.isBlank(text)) {
            return;
        }

        String normalText = normalize(text);
        String pureChinese = extractPureChinese(normalText);

        // 双重匹配：原文本或纯中文文本只要命中任何一棵树，直接抛异常
        boolean hit = isHitAnyTree(normalText) || (!pureChinese.isEmpty() && isHitAnyTree(pureChinese));

        ThrowUtils.throwIf(hit, ErrorCode.PARAMS_ERROR,
                fieldName + "包含敏感或违规内容，请修改后重试");
    }

    /**
     * 判断文本是否在任一棵树（拦截 / 审核 / 替换）中命中。
     *
     * @param text 待匹配文本（已归一化或已提取纯中文）
     * @return 命中任一棵树返回 {@code true}，否则 {@code false}
     */
    private boolean isHitAnyTree(String text) {
        return !interceptTree.matchAll(text).isEmpty()
                || !reviewTree.matchAll(text).isEmpty()
                || !replaceTree.matchAll(text).isEmpty();
    }

    /**
     * 内容发布场景的过滤入口，返回结构化处置结果，由调用方联动业务状态机。
     *
     * <p>采用<b>双重检测</b>：对原文做基础归一化得到 {@code normalText}，再提取纯中文 {@code pureChinese}，
     * 分别参与匹配。处置优先级：<b>拦截 &gt; 审核 &gt; 替换</b>——同一文本同时命中多类词时取最严处置。
     * 空白文本直接返回 {@code pass()}。</p>
     *
     * <p>替换环节仅对 {@code normalText} 进行：因为纯中文文本在命中替换词时，原文本含干扰字符，
     * 定位并替换原文本索引坐标的计算复杂度极高、性价比低；轻量词汇漏一两个变体无伤大雅，
     * 高危词汇已在拦截/审核环节被拦截。</p>
     *
     * @param text 待过滤文本
     * @return 处置结果 {@link FilterResult}
     */
    public FilterResult filter(String text) {
        if (StrUtil.isBlank(text)) {
            return FilterResult.pass();
        }

        // 1. 基础归一化（去空格 + 全角转半角 + 小写）
        String normalText = normalize(text);

        // 2. 纯中文提取（去除所有字母、数字、符号，降维打击插字绕过）
        String pureChinese = extractPureChinese(normalText);

        // --- 拦截判断 (最高优先级) ---
        // 两个维度的文本，任一命中拦截树即执行拦截
        if (!interceptTree.matchAll(normalText).isEmpty() ||
                (!pureChinese.isEmpty() && !interceptTree.matchAll(pureChinese).isEmpty())) {
            return FilterResult.intercept();
        }

        // --- 审核判断 (次优先级) ---
        if (!reviewTree.matchAll(normalText).isEmpty() ||
                (!pureChinese.isEmpty() && !reviewTree.matchAll(pureChinese).isEmpty())) {
            return FilterResult.review();
        }

        // --- 替换判断 (最低优先级) ---
        // 工程实践考量：只对 normalText 进行替换匹配。
        // 因为如果 pureChinese 命中，由于原文本含有干扰字符（如“脑x残”），
        // 定位并替换原文本的索引坐标计算极其复杂，性价比极低。
        // 辱骂等轻量级词汇漏一两个变体无伤大雅，高危词汇已在上方被拦截。
        List<String> hits = replaceTree.matchAll(normalText);
        if (!hits.isEmpty()) {
            String replaced = normalText;
            for (String w : hits) {
                replaced = replaced.replace(w, "***");
            }
            return FilterResult.replaced(replaced);
        }

        return FilterResult.pass();
    }

    /**
     * 基础归一化：去空格 + 全角转半角 + 小写。
     *
     * <p>用于对抗“敏 感 词”（插空格）、“ＶＸ”（全角字母）等简单变体，
     * 作为双重检测的第一维文本。</p>
     *
     * @param text 原始文本
     * @return 归一化后的文本
     */
    private String normalize(String text) {
        return Convert.toDBC(StrUtil.cleanBlank(text)).toLowerCase();
    }

    /**
     * 提取纯中文文本，过滤掉所有非中文字符（英文字母、数字、符号、零宽字符等）。
     *
     * <p>作为双重检测的第二维文本：把“澳s门s赌s场wud”降维为“澳门赌场”后参与匹配，
     * 可有效抵御在敏感词中间插入干扰字符的绕过手法。注意：仅保留汉字，
     * 因此“澳门的赌场”（含汉字“的”）不会被误拼成“澳门赌场”。</p>
     *
     * @param text 已归一化的文本
     * @return 仅含汉字的文本；若原文本无任何汉字则返回空字符串
     */
    private String extractPureChinese(String text) {
        // 利用正则表达式过滤非中文字符
        return text.replaceAll("[^\\u4e00-\\u9fa5]", "");
    }

    /**
     * 处置动作枚举，对应 {@link FilterResult#action} 的取值。
     */
    public enum SensitiveAction {
        /** 放行 */
        PASS,
        /** 拦截（拒绝发布） */
        INTERCEPT,
        /** 进入审核 */
        REVIEW,
        /** 已替换后发布 */
        REPLACED
    }

    /**
     * 过滤结果封装。
     *
     * <p>包含处置动作 {@link #action} 与脱敏后的文本 {@link #processedText}
     * （仅在 {@code REPLACED} 时非空）。建议使用静态工厂方法构造实例。</p>
     */
    public static class FilterResult {
        /** 处置动作 */
        public final SensitiveAction action;
        /** 处理后的文本（替换动作时为脱敏文本，其余为 null） */
        public final String processedText;

        private FilterResult(SensitiveAction action, String processedText) {
            this.action = action;
            this.processedText = processedText;
        }

        /** 构造“放行”结果 */
        public static FilterResult pass() {
            return new FilterResult(SensitiveAction.PASS, null);
        }

        /** 构造“拦截”结果 */
        public static FilterResult intercept() {
            return new FilterResult(SensitiveAction.INTERCEPT, null);
        }

        /** 构造“送审”结果 */
        public static FilterResult review() {
            return new FilterResult(SensitiveAction.REVIEW, null);
        }

        /** 构造“替换后发布”结果，携带脱敏文本 */
        public static FilterResult replaced(String text) {
            return new FilterResult(SensitiveAction.REPLACED, text);
        }
    }
}