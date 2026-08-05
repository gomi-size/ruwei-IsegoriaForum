package com.ruwei.service.impl;


import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.domain.empty.Tag;
import com.ruwei.service.TagService;
import com.ruwei.mapper.TagMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author Administrator
* @description 针对表【tag(标签表)】的数据库操作Service实现
* @createDate 2026-08-05 10:24:02
*/
@Service
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag>
    implements TagService{

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
}




