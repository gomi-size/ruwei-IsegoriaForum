package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.empty.Tag;

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
}
