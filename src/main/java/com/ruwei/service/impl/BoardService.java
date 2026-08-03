package com.ruwei.service.impl;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.BoardUpdateDTO;
import com.ruwei.domain.dto.CreateBoardDTO;
import com.ruwei.domain.empty.Board;


/**
* @author Administrator
* @description 针对表【board(贴吧板块表)】的数据库操作Service
* @createDate 2026-08-03 09:33:16
*/
public interface BoardService extends IService<Board> {

    /**
     * 创建贴吧
     * @param createBoardDTO
     * @return
     */
    Board createBoard(CreateBoardDTO createBoardDTO);

    /**
     * 更改贴吧
     * @param boardUpdateDTO
     */
    void updateBoard(BoardUpdateDTO boardUpdateDTO);
}
