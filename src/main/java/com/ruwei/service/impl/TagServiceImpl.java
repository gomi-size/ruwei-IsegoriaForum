package com.ruwei.service.impl;


import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.TagDTO;
import com.ruwei.domain.empty.PostTag;
import com.ruwei.domain.empty.Tag;
import com.ruwei.domain.vo.TagVO;
import com.ruwei.mapper.TagMapper;
import com.ruwei.service.PostTagService;
import com.ruwei.service.TagService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
* @author Administrator
* @description 针对表【tag(标签表)】的数据库操作Service实现
* @createDate 2026-08-05 10:24:02
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService{

    @Resource
    private PostTagService postTagService;

    /**
     * 热门标签榜（仅 status=1 正常标签，按 useCount 倒序，LIMIT 限制条数）。
     */
    @Override
    public List<Tag> getHotTags(int limit) {
        return lambdaQuery()
                .eq(Tag::getStatus, 1)
                .orderByDesc(Tag::getUseCount)
                .last("LIMIT " + limit)
                .list();
    }

    /**
     * 新增标签（name 唯一，useCount=0、status=1）。
     */
    @Override
    public TagVO addTag(TagDTO dto) {
        ThrowUtils.throwIf(BeanUtil.isEmpty(dto) || StrUtil.isBlank(dto.getName()),
                ErrorCode.PARAMS_ERROR, "标签名不能为空");
        String name = dto.getName().trim();
        ThrowUtils.throwIf(name.length() > 64, ErrorCode.PARAMS_ERROR, "标签名最多64字");

        // name 唯一（ukName 兜底，先查做友好提示）
        Long exists = lambdaQuery().eq(Tag::getName, name).count();
        ThrowUtils.throwIf(exists != null && exists > 0, ErrorCode.OPERATION_ERROR, "标签已存在");

        Tag tag = new Tag();
        tag.setName(name);
        tag.setUseCount(0);
        tag.setStatus(1);
        boolean saved = save(tag);
        ThrowUtils.throwIf(!saved, ErrorCode.OPERATION_ERROR, "新增失败");
        return BeanUtil.copyProperties(tag, TagVO.class);
    }

    /**
     * 更新标签名（排除自身后校验 name 唯一）。
     */
    @Override
    public void updateTag(Long id, TagDTO dto) {
        ThrowUtils.throwIf(id == null || BeanUtil.isEmpty(dto) || StrUtil.isBlank(dto.getName()),
                ErrorCode.PARAMS_ERROR, "参数不能为空");
        Tag tag = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(tag), ErrorCode.NOT_FOUND_ERROR, "标签不存在");

        String name = dto.getName().trim();
        ThrowUtils.throwIf(name.length() > 64, ErrorCode.PARAMS_ERROR, "标签名最多64字");
        // 排除自身：其余同名标签不允许
        Long exists = lambdaQuery().eq(Tag::getName, name).ne(Tag::getId, id).count();
        ThrowUtils.throwIf(exists != null && exists > 0, ErrorCode.OPERATION_ERROR, "标签名已被占用");

        boolean updated = lambdaUpdate().eq(Tag::getId, id).set(Tag::getName, name).update();
        ThrowUtils.throwIf(!updated, ErrorCode.OPERATION_ERROR, "更新失败");
    }

    /**
     * 删除标签（物理删除，并清理 post_tag 关联，避免孤儿引用）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteTag(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "标签 id 不能为空");
        Tag tag = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(tag), ErrorCode.NOT_FOUND_ERROR, "标签不存在");

        // 清理关联（post_tag 引用该标签的记录一并删除）
        postTagService.remove(new LambdaQueryWrapper<PostTag>().eq(PostTag::getTagId, id));

        boolean removed = removeById(id);
        ThrowUtils.throwIf(!removed, ErrorCode.OPERATION_ERROR, "删除失败");
    }

    /**
     * 按 id 查询标签详情。
     */
    @Override
    public TagVO getTag(Long id) {
        ThrowUtils.throwIf(id == null, ErrorCode.PARAMS_ERROR, "标签 id 不能为空");
        Tag tag = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(tag), ErrorCode.NOT_FOUND_ERROR, "标签不存在");
        return BeanUtil.copyProperties(tag, TagVO.class);
    }

    /**
     * 标签列表（仅 status=1 正常标签，按使用次数倒序）。
     */
    @Override
    public List<TagVO> listTags() {
        return lambdaQuery()
                .eq(Tag::getStatus, 1)
                .orderByDesc(Tag::getUseCount)
                .list().stream()
                .map(t -> BeanUtil.copyProperties(t, TagVO.class))
                .toList();
    }
}
