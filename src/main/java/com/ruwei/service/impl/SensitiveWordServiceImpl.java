package com.ruwei.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import cn.hutool.core.bean.BeanUtil;
import com.ruwei.exception.BusinessException;
import com.ruwei.common.ErrorCode;
import com.ruwei.component.SensitiveWordFilter;
import com.ruwei.domain.dto.SensitiveWordAddDTO;
import com.ruwei.domain.empty.SensitiveWord;
import com.ruwei.domain.vo.SensitiveWordVO;
import com.ruwei.mapper.SensitiveWordMapper;
import com.ruwei.service.SensitiveWordService;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class SensitiveWordServiceImpl extends ServiceImpl<SensitiveWordMapper, SensitiveWord>
        implements SensitiveWordService {

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    /**
     * 新增单个敏感词。
     *
     * <p>落库成功后调用 {@link SensitiveWordFilter#refresh()} 热刷新内存词库；
     * 若词已存在（命中唯一约束 {@code uk_word}），转换为
     * {@code PARAMS_ERROR: 敏感词已存在} 业务异常。</p>
     *
     * @param dto 敏感词入参（word 必填）
     * @return 写入成功返回 {@code true}，否则 {@code false}
     */
    @Override
    public boolean add(SensitiveWordAddDTO dto) {
        SensitiveWord sw = new SensitiveWord();
        sw.setWord(dto.getWord());
        sw.setCategory(dto.getCategory() == null ? 1 : dto.getCategory());
        sw.setAction(dto.getAction() == null ? 1 : dto.getAction());
        try {
            boolean saved = save(sw);
            if (saved) {
                sensitiveWordFilter.refresh();
            }
            return saved;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "敏感词已存在");
        }
    }

    /**
     * 批量新增敏感词（一次传多组）。
     *
     * <p>自动跳过 {@code null} 或 word 为空白的项；使用 MyBatis-Plus {@code saveBatch}
     * 一次性落库，随后仅触发<b>一次</b> {@link SensitiveWordFilter#refresh()} 刷新内存词库。
     * 若批次内或库中已存在重复 word（命中唯一约束），整体抛出
     * {@code PARAMS_ERROR: 存在重复或已存在的敏感词}。</p>
     *
     * @param dtos 敏感词入参列表，每项可单独指定 category / action
     * @return 成功写入的条数（已跳过空/空白项）
     */
    @Override
    public int addBatch(List<SensitiveWordAddDTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return 0;
        }
        List<SensitiveWord> entities = new ArrayList<>();
        for (SensitiveWordAddDTO d : dtos) {
            if (d == null || d.getWord() == null || d.getWord().isBlank()) {
                continue;
            }
            SensitiveWord sw = new SensitiveWord();
            sw.setWord(d.getWord().trim());
            sw.setCategory(d.getCategory() == null ? 1 : d.getCategory());
            sw.setAction(d.getAction() == null ? 1 : d.getAction());
            entities.add(sw);
        }
        if (entities.isEmpty()) {
            return 0;
        }
        try {
            saveBatch(entities);
            sensitiveWordFilter.refresh();
            return entities.size();
        } catch (DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "存在重复或已存在的敏感词");
        }
    }

    /**
     * 根据主键删除敏感词。
     *
     * <p>删除成功后调用 {@link SensitiveWordFilter#refresh()} 热刷新内存词库。</p>
     *
     * @param id 敏感词记录主键
     * @return 删除成功返回 {@code true}，否则 {@code false}
     */
    @Override
    public boolean deleteWord(Long id) {
        boolean removed = removeById(id);
        if (removed) {
            sensitiveWordFilter.refresh();
        }
        return removed;
    }

    /**
     * 查询全部敏感词列表（按数据库全量返回，复制为 VO 不下发实体）。
     *
     * @return 敏感词 VO 列表
     */
    @Override
    public List<SensitiveWordVO> listAll() {
        return list().stream()
                .map(sw -> BeanUtil.copyProperties(sw, SensitiveWordVO.class))
                .toList();
    }
}
