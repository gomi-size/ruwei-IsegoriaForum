package com.ruwei.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.ruwei.domain.dto.PostDTO;
import com.ruwei.domain.empty.Post;


/**
* @author Administrator
* @description 针对表【post(帖子/笔记表(推荐系统物料主表))】的数据库操作Service
* @createDate 2026-08-05 10:16:16
*
* <p><b>审核状态机约定（2026-08-05）：</b>
* <ul>
*   <li>{@code status}（生命周期）：1已发布 2草稿 3审核中 4下架（5删除废弃，删除统一走 {@code isDelete} 逻辑删除）；</li>
*   <li>{@code auditStatus}（审核结果）：1待审 2通过 3驳回；</li>
*   <li><b>创建送审</b>：createPost 后 status=3 + auditStatus=1，管理员审核通过才对外可见；</li>
*   <li><b>编辑先发后审</b>：updatePost 把新内容暂存 pending* 字段并置 status=3+auditStatus=1，
*       正式字段不动（旧内容继续展示），审核通过才应用 pending、驳回则丢弃；</li>
*   <li><b>设置状态不走审核</b>：updatePostStatus 由作者直接切换 草稿/发布/下架。</li>
* </ul></p>
*/
public interface PostService extends IService<Post> {

    /**
     * 创建帖子（送审：status=3 审核中 + auditStatus=1 待审，审核通过后对外可见）。
     * 作者取当前登录态；内容过敏感词 filter；写 post + post_image + tag/post_tag；板块帖子数 +1。
     *
     * @param dto 创建入参（title/content 必填，images/tags 可选）
     * @return 创建后的帖子实体（含对外编码 postCode）
     */
    Post createPost(PostDTO dto);

    /**
     * 编辑帖子（先发后审）：
     * <ul>
     *   <li>草稿（status=2）：直接改正式字段，不走审核；</li>
     *   <li>已发布/审核中/下架：新内容暂存 pending* 字段 + status=3 + auditStatus=1（旧内容继续对外），
     *       由管理员审核通过后应用或驳回丢弃。</li>
     * </ul>
     * 仅作者本人可编辑。
     *
     * @param dto 编辑入参（id 必传）
     */
    void updatePost(PostDTO dto);

    /**
     * 设置帖子状态（不走审核，作者本人操作）。
     * 允许设置：1已发布 / 2草稿 / 4下架；审核中（status=3）不允许手动改，等待审核结果。
     * 草稿→发布时同步置 auditStatus=2（内容未公开过，无需审核）。
     *
     * @param id     帖子内部主键
     * @param status 目标状态（1/2/4）
     */
    void updatePostStatus(Long id, Integer status);

    /**
     * 删除帖子（逻辑删除 isDelete=1，作者或管理员）：
     * 清理 post_image / post_tag 关联（物理删），关联标签 useCount 回退，板块帖子数 -1。
     *
     * @param id 帖子内部主键
     */
    void deletePost(Long id);

    /**
     * 管理员审核帖子。
     *
     * @param id   帖子内部主键
     * @param pass true=通过（应用 pending 覆盖正式内容 → status=1 + auditStatus=2，首次发布时板块帖子数 +1）；
     *             false=驳回（丢弃 pending；编辑驳回→恢复旧版对外 status=1+auditStatus=3，
     *             创建驳回→帖子不可见 status=4+auditStatus=3）
     */
    void auditPost(Long id, Boolean pass);
}
