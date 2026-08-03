package com.ruwei.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ruwei.common.BaseResponse;
import com.ruwei.common.ResultUtils;
import com.ruwei.domain.dto.BoardUpdateDTO;
import com.ruwei.domain.dto.CreateBoardDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.service.impl.BoardService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.*;

/**
 * 贴吧
 */
@RestController
@RequestMapping("/board")
@SaCheckLogin
public class BoardController {

    @Resource
    private BoardService boardService;




    /**
     *创建吧主
     */
    @PostMapping("/create")
    public BaseResponse<Board> createBoard(@RequestBody CreateBoardDTO createBoardDTO){

        Board board= boardService.createBoard(createBoardDTO);

        return ResultUtils.success(board);
    }

    /**
     * 编辑吧主信息
     */
    @PostMapping("/update")
    public BaseResponse<String> updateBoard(@RequestBody BoardUpdateDTO boardUpdateDTO){
        boardService.updateBoard(boardUpdateDTO);
        return ResultUtils.success("修改成功");
    }



}
