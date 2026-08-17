package com.ruwei.service.impl;


import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.ruwei.common.ErrorCode;
import com.ruwei.common.ThrowUtils;
import com.ruwei.component.SensitiveWordFilter;
import com.ruwei.domain.Enum.PostVisibilityEnum;
import com.ruwei.domain.dto.CommentAddDTO;
import com.ruwei.domain.empty.Comment;
import com.ruwei.domain.empty.Post;
import com.ruwei.manager.FollowCacheManager;
import com.ruwei.service.CommentService;
import com.ruwei.mapper.CommentMapper;
import com.ruwei.service.PostService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
* @author Administrator
* @description 针对表【comment(评论表(两级盖楼))】的数据库操作Service实现
* @createDate 2026-08-14 16:44:41
*/
@Service
public class CommentServiceImpl extends ServiceImpl<CommentMapper, Comment>
    implements CommentService{

    @Resource
    private SensitiveWordFilter sensitiveWordFilter;

    @Resource
    private PostService postService;

    @Resource
    private FollowCacheManager followCacheManager;
    /**
     * 添加评论
     * @param commentAddDTO
     */
    @Override
    public void addComment(CommentAddDTO commentAddDTO) {

        //获取到数据
        String content = commentAddDTO.getContent();
        Long postId = commentAddDTO.getPostCode();
        Post post = postService.getById(postId);
        ThrowUtils.throwIf(BeanUtil.isEmpty(post),ErrorCode.NOT_FOUND_ERROR,"帖子不存在");
        long loginId = StpUtil.getLoginIdAsLong();

        //进行敏感词处理和鉴权
        //敏感词处理
        String scrubContent = scrub(content, "评论");
        commentAddDTO.setContent(scrubContent);
        ThrowUtils.throwIf(scrubContent.length()>1000,ErrorCode.PARAMS_ERROR,"评论字数过长");

        //进行鉴权，判断帖子的状态是不是只有粉丝才能查看和评论粉丝
        Integer visibility = post.getVisibility();
        if(PostVisibilityEnum.FANS_ONLY.matches(visibility)){
            Boolean following = followCacheManager.isFollowing(loginId, post.getUserId());
            ThrowUtils.throwIf(!following,ErrorCode.NO_AUTH_ERROR,"你不是该作者的粉丝，请关注");
        }
        //判断是否为私密
        if(PostVisibilityEnum.PRIVATE.matches(visibility) ){
            ThrowUtils.throwIf(true,ErrorCode.NO_AUTH_ERROR,"该帖子未公开");
        }


        Comment comment = BeanUtil.copyProperties(commentAddDTO, Comment.class);
        comment.setUserId(loginId);
        boolean save = save(comment);
        ThrowUtils.throwIf(!save,ErrorCode.OPERATION_ERROR,"评论失败");

    }











    /**
     * 敏感词扫描与处置
     * 命中拦截词直接拒绝；命中替换词脱敏为 ***；审核词/放行保留原文。
     */
    private String scrub(String text, String fieldName) {
        if (StrUtil.isBlank(text)) {
            return text;
        }
        SensitiveWordFilter.FilterResult fr = sensitiveWordFilter.filter(text);
        ThrowUtils.throwIf(fr.action == SensitiveWordFilter.SensitiveAction.INTERCEPT,
                ErrorCode.PARAMS_ERROR, fieldName + "包含敏感或违规内容，请修改后重试");
        if (fr.action == SensitiveWordFilter.SensitiveAction.REPLACED) {
            return fr.processedText;
        }
        return text;
    }

}




