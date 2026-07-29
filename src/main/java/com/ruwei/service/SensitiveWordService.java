package com.ruwei.service;

import com.ruwei.domain.dto.SensitiveWordAddDTO;
import com.ruwei.domain.empty.SensitiveWord;
import java.util.List;

/**
 * 敏感词业务接口。
 *
 * <p>所有写操作（新增/批量新增/删除）在落库成功后都会触发
 * {@code SensitiveWordFilter.refresh()}，将内存中的 DFA Trie 热刷新为最新词库，
 * 保证过滤器与数据库一致、无需重启。</p>
 */
public interface SensitiveWordService {

    /**
     * 新增单个敏感词。
     *
     * @param dto 敏感词入参（word 必填，category/action 可缺省取默认）
     * @return 写入成功返回 {@code true}，否则 {@code false}
     */
    boolean add(SensitiveWordAddDTO dto);

    /**
     * 批量新增敏感词（一次传多组）。
     *
     * @param dtos 敏感词入参列表，每项可单独指定 category / action
     * @return 成功写入的条数（已跳过空/空白项）
     */
    int addBatch(Object body);

    /**
     * 根据主键删除敏感词。
     *
     * @param id 敏感词记录主键
     * @return 删除成功返回 {@code true}，否则 {@code false}
     */
    boolean deleteWord(Long id);

    /**
     * 查询全部敏感词列表（按数据库全量返回）。
     *
     * @return 敏感词实体列表
     */
    List<SensitiveWord> listAll();
}
