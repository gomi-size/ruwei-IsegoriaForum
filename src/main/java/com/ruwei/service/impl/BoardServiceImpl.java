package com.ruwei.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.domain.dto.BoardUpdateDTO;
import com.ruwei.domain.dto.CreateBoardDTO;
import com.ruwei.domain.empty.Board;
import com.ruwei.mapper.BoardMapper;
import com.ruwei.service.UserService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【board(贴吧板块表)】的数据库操作Service实现
* @createDate 2026-08-03 09:33:16
*/
@Service
public class BoardServiceImpl extends ServiceImpl<BoardMapper, Board>
    implements BoardService{

    @Resource
    private UserService userService;

    /**
     * 创建贴吧
     * @param createBoardDTO
     * @return
     */
    @Override
    public Board createBoard(CreateBoardDTO createBoardDTO) {
        //校验参数
        String name = createBoardDTO.getName();
        String slug = createBoardDTO.getSlug();

        ThrowUtils.throwIf(BeanUtil.isEmpty(createBoardDTO)||name==null||slug==null, ErrorCode.PARAMS_ERROR,"贴吧名字和标识不能为空");
        long loginId = StpUtil.getLoginIdAsLong();

        boolean exists = lambdaQuery().eq(Board::getName, name).exists();
        ThrowUtils.throwIf(exists,ErrorCode.PARAMS_ERROR,"改吧名已经存在");

        Board board = BeanUtil.copyProperties(createBoardDTO, Board.class);
        board.setCreatorId(loginId);
        boolean save = save(board);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"创建失败");


        return board;
    }

    /**
     * 更改贴吧信息
     * @param boardUpdateDTO
     */
    @Override
    public void updateBoard(BoardUpdateDTO boardUpdateDTO) {
        Boolean isAdmin = userService.isAdmin();
        long loginId = StpUtil.getLoginIdAsLong();
        ThrowUtils.throwIf(loginId!=boardUpdateDTO.getCreatorId()&&!isAdmin,ErrorCode.NO_AUTH_ERROR,"只能管理员和创建本吧主修改");


        ThrowUtils.throwIf(boardUpdateDTO.getName()==null,ErrorCode.PARAMS_ERROR,"参数错误");
        ThrowUtils.throwIf(boardUpdateDTO.getId()==null,ErrorCode.PARAMS_ERROR,"参数错误");
        Long id = boardUpdateDTO.getId();
        Board oldBoard = getById(id);
        ThrowUtils.throwIf(BeanUtil.isEmpty(oldBoard),ErrorCode.NOT_FOUND_ERROR,"修改失败,该吧不存在");

        if(oldBoard.getName().equals(boardUpdateDTO.getName())){
            boolean result = updateById(BeanUtil.copyProperties(boardUpdateDTO, Board.class));
            ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"更新失败");
        }else {
            boolean exists = lambdaQuery().eq(Board::getName, boardUpdateDTO.getName()).exists();
            ThrowUtils.throwIf(exists,ErrorCode.PARAMS_ERROR,"改吧名已经存在");
            boolean result = updateById(BeanUtil.copyProperties(boardUpdateDTO, Board.class));
            ThrowUtils.throwIf(!result,ErrorCode.OPERATION_ERROR,"更新失败");
        }
    }



}




