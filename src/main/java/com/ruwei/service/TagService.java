package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.TagDTO;
import com.ruwei.domain.empty.Tag;
import com.ruwei.domain.vo.TagVO;

import java.util.List;


/**
* @author Administrator
* @description 针对表【tag(标签表)】的数据库操作Service
* @createDate 2026-08-05 10:24:02
*/
public interface TagService extends IService<Tag> {

    /**
     * 热门标签榜（仅正常标签，按使用次数倒序）。
     *
     * @param limit 返回条数上限（如 20）
     * @return 热门标签列表
     */
    List<Tag> getHotTags(int limit);

    /**
     * 新增标签（name 唯一，useCount=0、status=1）。
     *
     * @param dto 入参（name 必填）
     * @return 新增后的标签 VO
     */
    TagVO addTag(TagDTO dto);

    /**
     * 更新标签名（排除自身后校验 name 唯一）。
     *
     * @param id  标签内部主键
     * @param dto 入参（name 必填）
     */
    void updateTag(Long id, TagDTO dto);

    /**
     * 删除标签（物理删除，并清理 post_tag 关联，避免孤儿引用）。
     *
     * @param id 标签内部主键
     */
    void deleteTag(Long id);

    /**
     * 按 id 查询标签详情。
     *
     * @param id 标签内部主键
     * @return 标签 VO
     */
    TagVO getTag(Long id);

    /**
     * 标签列表（仅 status=1 正常标签，按使用次数倒序）。
     *
     * @return 标签 VO 列表
     */
    List<TagVO> listTags();
}
