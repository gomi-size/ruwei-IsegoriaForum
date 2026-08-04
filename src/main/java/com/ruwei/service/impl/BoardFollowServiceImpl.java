package com.ruwei.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruwei.domain.empty.BoardFollow;
import com.ruwei.service.BoardFollowService;
import com.ruwei.mapper.BoardFollowMapper;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【board_follow(用户关注板块表)】的数据库操作Service实现
* @createDate 2026-08-04 14:11:16
*/
@Service
public class BoardFollowServiceImpl extends ServiceImpl<BoardFollowMapper, BoardFollow>
    implements BoardFollowService{

}




