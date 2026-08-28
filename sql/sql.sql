# ============================================================
# ISEGORIA 论坛 —— 完整建表脚本（10 张表，与 domain/empty 实体一一对应）
#
# 使用说明：
#   - 新建库：直接执行下方全部 CREATE TABLE 即可；
#   - 已按旧版建过库：执行文件末尾「旧库迁移」区块。
#
# 列名约定（重要）：
#   application.yml 配置了 mybatis-plus.map-underscore-to-camel-case: false，
#   列名必须与实体字段名完全一致（驼峰风格），否则 MyBatis-Plus 拼 SQL / 映射会失败。
# ============================================================

# ------------------------------------------------------------
# 1. 用户表（domain/empty/User）
# ------------------------------------------------------------
CREATE TABLE `user` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        `userId` BIGINT DEFAULT NULL COMMENT '对外展示的唯一编码',
                        `username` VARCHAR(64) NOT NULL COMMENT '登录名(手机或邮箱)',
                        `nickname` VARCHAR(64) DEFAULT '' COMMENT '昵称',
                        `password` VARCHAR(255) NOT NULL COMMENT 'bcrypt加密',
                        `avatar` VARCHAR(255) DEFAULT '' COMMENT '头像URL',
                        `gender` TINYINT DEFAULT 0 COMMENT '0未知 1男 2女',
                        `birthday` DATETIME DEFAULT NULL COMMENT '生日',
                        `bio` VARCHAR(255) DEFAULT '' COMMENT '个性签名',
                        `location` VARCHAR(64) DEFAULT '' COMMENT '所在地',
                        `level` INT DEFAULT 1 COMMENT '等级',
                        `exp` INT DEFAULT 0 COMMENT '经验值',
                        `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
                        `email` VARCHAR(64) DEFAULT NULL COMMENT '邮箱',
                        `status` TINYINT DEFAULT 1 COMMENT '1正常 2禁用 3注销',
                        `followCount` INT DEFAULT 0 COMMENT '关注数',
                        `fansCount` INT DEFAULT 0 COMMENT '粉丝数',
                        `postCount` INT DEFAULT 0 COMMENT '发帖数',
                        `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建日期',
                        `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改日期',
                        `isDelete` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
                        `admin` TINYINT DEFAULT 0 COMMENT '是否管理员: 1是 0否',
                        PRIMARY KEY (`id`),
                        UNIQUE KEY `uk_username` (`username`),
                        KEY `idx_userId` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

# ------------------------------------------------------------
# 2. 贴吧板块表（domain/empty/Board）
# ------------------------------------------------------------
CREATE TABLE `board` (
                         `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                         `name` VARCHAR(64) NOT NULL COMMENT '吧名',
                         `slug` VARCHAR(64) NOT NULL COMMENT '唯一标识',
                         `description` VARCHAR(255) DEFAULT '' COMMENT '简介',
                         `icon` VARCHAR(255) DEFAULT '' COMMENT '图标',
                         `creatorId` BIGINT NOT NULL COMMENT '创建者/吧主',
                         `followCount` INT DEFAULT 0 COMMENT '关注数',
                         `postCount` INT DEFAULT 0 COMMENT '帖子数',
                         `levelRule` VARCHAR(255) DEFAULT '' COMMENT '等级头衔规则(JSON)',
                         `status` TINYINT DEFAULT 1 COMMENT '1正常 2封禁',
                         `isDelete` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0否 1是',
                         `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                         `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                         PRIMARY KEY (`id`),
                         UNIQUE KEY `uk_slug` (`slug`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='贴吧板块表';

# ------------------------------------------------------------
# 3. 用户关注板块表（domain/empty/BoardFollow）
# ------------------------------------------------------------
CREATE TABLE `board_follow` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                `userId` BIGINT NOT NULL COMMENT '关注者',
                                `boardId` BIGINT NOT NULL COMMENT '板块',
                                `status` TINYINT DEFAULT 1 COMMENT '关注状态: 1关注 2已取消关注(软标记保留历史)',
                                `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_pair` (`userId`,`boardId`),
                                KEY `idx_board` (`boardId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注板块表';

# ------------------------------------------------------------
# 4. 用户关注关系表（domain/empty/UserFollow）
# ------------------------------------------------------------
CREATE TABLE `user_follow` (
                               `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                               `followerId` BIGINT NOT NULL COMMENT '主动关注者',
                               `followeeId` BIGINT NOT NULL COMMENT '被关注者',
                               `status` TINYINT DEFAULT 1 COMMENT '1关注 2已取消关注',
                               `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_pair` (`followerId`,`followeeId`),
                               KEY `idx_followee` (`followeeId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户关注关系表';

# ------------------------------------------------------------
# 5. 标签表（domain/empty/Tag）
# ------------------------------------------------------------
CREATE TABLE `tag` (
                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                       `name` VARCHAR(64) NOT NULL COMMENT '标签名(唯一)',
                       `useCount` INT DEFAULT 0 COMMENT '使用次数(热门标签榜排序依据)',
                       `status` TINYINT DEFAULT 1 COMMENT '1正常 2禁用',
                       PRIMARY KEY (`id`),
                       UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

# ------------------------------------------------------------
# 6. 帖子表（domain/empty/Post，推荐系统物料主表）
# ------------------------------------------------------------
CREATE TABLE `post` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                        `postCode` VARCHAR(32) NOT NULL COMMENT '对外唯一编码',
                        `userId` BIGINT NOT NULL COMMENT '作者',
                        `boardId` BIGINT DEFAULT NULL COMMENT '所属板块',
                        `title` VARCHAR(255) DEFAULT '' COMMENT '标题',
                        `content` TEXT COMMENT '正文(发布时过敏感词 filter；新数据为 ContentBlock JSON 数组)',
                        `cover` VARCHAR(255) DEFAULT '' COMMENT '封面图URL',
                        `type` TINYINT DEFAULT 1 COMMENT '1图文 2视频 3纯文',
                        `videoUrl` VARCHAR(255) DEFAULT '' COMMENT '视频地址',
                        `topic` VARCHAR(64) DEFAULT '' COMMENT '话题(标签id逗号串)',
                        `visibility` TINYINT DEFAULT 1 COMMENT '1公开 2仅粉丝可见 3私密(仅作者)',
                        `status` TINYINT DEFAULT 1 COMMENT '生命周期状态: 1已发布 2草稿 3审核中 4下架 5删除',
                        `likeCount` INT DEFAULT 0 COMMENT '点赞数(热度公式×1)',
                        `commentCount` INT DEFAULT 0 COMMENT '评论数(热度公式×2)',
                        `collectCount` INT DEFAULT 0 COMMENT '收藏数(热度公式×3)',
                        `viewCount` INT DEFAULT 0 COMMENT '浏览数(冷启动召回: viewCount<阈值)',
                        `shareCount` INT DEFAULT 0 COMMENT '分享数(热度公式×4)',
                        `dislikeCount` INT DEFAULT 0 COMMENT '拉踩(踩)数(热度公式负向降权, 权重默认-1)',
                        `reportCount` INT DEFAULT 0 COMMENT '举报数(热度公式负向重扣, 权重默认-5)',
                        `score` DECIMAL(12,4) DEFAULT 0 COMMENT '预计算热度分: (赞×1+评×2+藏×3+享×4+踩×(-1)+举报×(-5))×exp(-Δt/τ); 热点召回/粗排直接ORDER BY score DESC',
                        `isTop` TINYINT DEFAULT 0 COMMENT '置顶(重排强插第1/2位)',
                        `isEssence` TINYINT DEFAULT 0 COMMENT '精华',
                        `auditStatus` TINYINT DEFAULT 2 COMMENT '审核结果: 1待审 2通过 3驳回',
                        `latitude` DECIMAL(10,7) DEFAULT NULL COMMENT '纬度',
                        `longitude` DECIMAL(10,7) DEFAULT NULL COMMENT '经度',
                        `locationName` VARCHAR(64) DEFAULT '' COMMENT '位置名称',
                        `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
                        `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                        `isDelete` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删 1已删',
                        `draftOfId` BIGINT DEFAULT NULL COMMENT '草稿来源帖id(null=新建草稿, 非null=由正式帖编辑而来); 同一用户对同一来源最多一条草稿',
                        PRIMARY KEY (`id`),
                        KEY `idxBoard` (`boardId`,`isTop`,`createdAt`),
                        KEY `idxUser` (`userId`,`createdAt`),
                        KEY `idxStatus` (`status`),
                        KEY `idxTopic` (`topic`),
                        KEY `idxScore` (`score`),
                        KEY `idxDraft` (`userId`,`draftOfId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子/笔记表(推荐系统物料主表)';

# ------------------------------------------------------------
# 7. 帖子图片表（domain/empty/PostImage；一张帖子可有多个图片）
# ------------------------------------------------------------
CREATE TABLE `post_image` (
                              `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                              `postId` BIGINT NOT NULL COMMENT '所属帖子内部id',
                              `url` VARCHAR(255) NOT NULL COMMENT '图片URL',
                              `width` INT DEFAULT 0 COMMENT '图片宽度(px)',
                              `height` INT DEFAULT 0 COMMENT '图片高度(px)',
                              `sort` INT DEFAULT 0 COMMENT '排序序号(升序，封面取 sort 最小)',
                              `status` TINYINT DEFAULT 1 COMMENT '所属帖子版本: 1已发布 2草稿 3审核中 4下架',
                              PRIMARY KEY (`id`),
                              KEY `idxPostStatus` (`postId`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子图片表';

# ------------------------------------------------------------
# 8. 帖子标签关联表（domain/empty/PostTag）
# ------------------------------------------------------------
CREATE TABLE `post_tag` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                            `postId` BIGINT NOT NULL COMMENT '帖子内部id',
                            `tagId` BIGINT NOT NULL COMMENT '标签内部id',
                            `status` TINYINT DEFAULT 1 COMMENT '所属帖子版本: 1已发布 2草稿 3审核中 4下架',
                            PRIMARY KEY (`id`),
                            -- 唯一键必须带上 status：同一帖子的同一标签允许同时存在「已发布版」与「审核中版」
                            UNIQUE KEY `ukPostTag` (`postId`,`tagId`,`status`),
                            KEY `idxTag` (`tagId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子标签关联表';

# ------------------------------------------------------------
# 9. 通知表（domain/empty/Notification，历史存储/消息中心真相源）
# ------------------------------------------------------------
CREATE TABLE `notification` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                `receiverId` BIGINT NOT NULL COMMENT '接收者',
                                `senderId` BIGINT DEFAULT NULL COMMENT '触发者',
                                `type` TINYINT DEFAULT NULL COMMENT '1点赞 2评论 3回复 4关注 5@提及 6系统 7收藏',
                                `targetType` TINYINT DEFAULT NULL COMMENT '1帖子 2用户 3板块',
                                `targetId` BIGINT DEFAULT NULL COMMENT '关联对象内部id',
                                `commentId` BIGINT DEFAULT NULL COMMENT '评论内部id(评论/回复通知跳转锚点, 前端定位楼中楼评论)',
                                `content` VARCHAR(255) DEFAULT '' COMMENT '预览文案',
                                `bizKey` VARCHAR(128) DEFAULT NULL COMMENT '业务幂等键(如 like:{uid}:{postId}), 防重复通知',
                                `isRead` TINYINT DEFAULT 0 COMMENT '0未读 1已读',
                                `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_biz_key` (`bizKey`),
                                KEY `idx_receiver` (`receiverId`,`isRead`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='通知表(历史存储/消息中心真相源)';

# ------------------------------------------------------------
# 10. 敏感词表（domain/empty/SensitiveWord；过滤器数据源：启动/增删词时全表载入 DFA Trie）
# ------------------------------------------------------------
CREATE TABLE `sensitive_word` (
                                  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                                  `word` VARCHAR(64) NOT NULL COMMENT '敏感词内容(唯一防重)',
                                  `category` TINYINT DEFAULT 1 COMMENT '分类(仅后台筛选/统计): 1违禁 2广告 3辱骂…',
                                  `action` TINYINT DEFAULT 1 COMMENT '处置动作: 1替换***后发布 2直接拦截 3进入审核队列',
                                  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(数据库默认值维护, 代码层不赋值)',
                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='敏感词表(过滤数据源)';

# ============================================================
# 旧库迁移（已按旧版建过库的用户执行；新建库无需执行）
# 说明：
#   - 旧版 board / board_follow 列名是下划线风格，且缺若干列 → 改为驼峰 + 补列；
#   - RENAME COLUMN 需要 MySQL 8.0+；
#   - post 表旧库补 draftOfId；user / user_follow / notification / sensitive_word
#     旧库未建，直接执行上方对应的 CREATE TABLE。
# ============================================================

# 2. board_follow 表：下划线列 → 驼峰列 + 补 status
ALTER TABLE `board_follow`
    RENAME COLUMN `created_at` TO `createdAt`,
    ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '关注状态: 1关注 2已取消关注(软标记保留历史)' AFTER `boardId`;

# 3. post 表旧库补 draftOfId（草稿槽位）
ALTER TABLE `post`
    ADD COLUMN `draftOfId` BIGINT DEFAULT NULL COMMENT '草稿来源帖id(null=新建草稿)' AFTER `isDelete`,
    ADD KEY `idxDraft` (`userId`,`draftOfId`);

# 4. notification 表补 commentId（评论/回复通知跳转锚点）
ALTER TABLE `notification`
    ADD COLUMN `commentId` BIGINT DEFAULT NULL COMMENT '评论内部id(评论/回复通知跳转锚点, 前端定位楼中楼评论)' AFTER `targetId`;

# 审核行为日志表，同步落地
CREATE TABLE `auditLog` (
                            `id` BIGINT NOT NULL AUTO_INCREMENT,
                            `adminId` BIGINT NOT NULL,
                            `targetType` TINYINT NOT NULL COMMENT '1帖子 2评论',
                            `targetId` BIGINT NOT NULL,
                            `action` TINYINT NOT NULL COMMENT '1通过 2下架 3删除',
                            `remark` VARCHAR(255) DEFAULT '' COMMENT '审核操作的补充说明，例如拒绝原因、下架理由等',
                            `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP,
                            PRIMARY KEY (`id`),
                            KEY `idxTarget` (`targetType`,`targetId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='审核日志表';


#评论
CREATE TABLE `comment` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键(代码层雪花ASSIGN_ID, DB自增仅兜底)',
  `postId` BIGINT NOT NULL COMMENT '帖子内部id',
  `userId` BIGINT NOT NULL COMMENT '评论者内部id',
  `parentId` BIGINT NOT NULL DEFAULT 0 COMMENT '父评论id: 0=一级评论, 二级回复一律指向顶层评论(含楼中楼互评)',
  `replyToUserId` BIGINT DEFAULT NULL COMMENT '被回复用户内部id',
  `content` VARCHAR(1000) NOT NULL COMMENT '评论内容(发布时过敏感词 filter: 替换则存替换后文本, 拦截则拒绝)',
  `likeCount` INT DEFAULT 0 COMMENT '点赞数(CountUtils原子增减)',
  `replyCount` INT DEFAULT 0 COMMENT '子回复数(仅顶层评论有意义, 只统计status=1)',
  `status` TINYINT DEFAULT 1 COMMENT '1正常 2已删除(软删, 列表需展示"已删除"占位)',
  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`),
  KEY `idxPostParent` (`postId`,`parentId`,`createdAt`),
  KEY `idxParent` (`parentId`),
  KEY `idxUser` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论表(两级盖楼)';


CREATE TABLE `comment_like` (
                                `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键(雪花)',
                                `commentId` BIGINT NOT NULL COMMENT '评论内部id',
                                `userId` BIGINT NOT NULL COMMENT '点赞者内部id',
                                `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `ukCommentUser` (`commentId`,`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='评论点赞表';

CREATE TABLE `post_like` (
                             `id` BIGINT NOT NULL AUTO_INCREMENT,
                             `postId` BIGINT NOT NULL,
                             `userId` BIGINT NOT NULL,
                             `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP,
                             PRIMARY KEY (`id`),
                             UNIQUE KEY `ukPostUser` (`postId`,`userId`),
                             KEY `idxUser` (`userId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子点赞表';

# 用户行为日志
CREATE TABLE `userBehavior` (
                                `id` BIGINT NOT NULL COMMENT '主键(雪花 ASSIGN_ID)',
                                `userId` BIGINT NOT NULL COMMENT '用户内部id',
                                `postId` BIGINT NOT NULL COMMENT '帖子内部id',
                                `action` TINYINT NOT NULL COMMENT '1曝光 2点击进入 3浏览/停留 4点赞 5评论 6收藏 7分享 8负反馈',
                                `source` TINYINT DEFAULT 0 COMMENT '来源: 1推荐流 2关注流 3板块流 4搜索 5热榜 0未知',
                                `position` SMALLINT DEFAULT 0 COMMENT '信息流展示位次(第几条, 用于去偏)',
                                `dwellSec` INT DEFAULT 0 COMMENT '停留时长(秒), action=3 有效',
                                `extras` VARCHAR(255) DEFAULT '' COMMENT '上下文JSON: {net,hour,city}',
                                `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '行为时间',
                                PRIMARY KEY (`id`),
                                KEY `idxUserPost` (`userId`,`postId`),
                                KEY `idxCreated` (`createdAt`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户行为日志';

#用户长期兴趣画像
CREATE TABLE `user_interest` (
 `id` BIGINT NOT NULL COMMENT '主键(雪花 ASSIGN_ID)',
 `userId` BIGINT NOT NULL COMMENT '用户内部id(=loginId)',
 `dimension` TINYINT NOT NULL COMMENT '1话题 2标签 3类型 4板块 5作者',
 `value` VARCHAR(64) NOT NULL COMMENT 'topic名 / tagId / type码(1图文2视频3纯文) / boardId / authorId',
 `weight` DECIMAL(6,4) NOT NULL DEFAULT 0 COMMENT '兴趣权重(0~1+, 越大越感兴趣)',
 `lastActiveAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次强化时间',
 `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP,
 `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY (`id`),
 UNIQUE KEY `ukUserDimVal` (`userId`,`dimension`,`value`),
 KEY `idxUserDim` (`userId`,`dimension`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户长期兴趣画像';

# ------------------------------------------------------------
# 11. 热度评分参数配置表（单行配置，ScoreRecalcJob 每 5 分钟按此计算帖子热度分）
#     权重语义：likeW/commentW/collectW/shareW 为正向加分项；
#               dislikeW/reportW 为负向降权项（配置负数，如 -1.0 / -5.0）；
#               tauHours 为时间衰减半衰期(小时)，必须 > 0（防除零）。
#     管理端接口：GET/PUT /admin/score-config（改后内存热刷新，无需重启）
# ------------------------------------------------------------
CREATE TABLE `score_config` (
`id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
`likeW` DOUBLE NOT NULL DEFAULT 1.0 COMMENT '点赞权重',
`commentW` DOUBLE NOT NULL DEFAULT 2.0 COMMENT '评论权重',
`collectW` DOUBLE NOT NULL DEFAULT 3.0 COMMENT '收藏权重',
`shareW` DOUBLE NOT NULL DEFAULT 4.0 COMMENT '分享权重',
`dislikeW` DOUBLE NOT NULL DEFAULT -1.0 COMMENT '拉踩(踩)权重, 负值扣分',
`reportW` DOUBLE NOT NULL DEFAULT -5.0 COMMENT '举报权重, 负值重扣',
`tauHours` DOUBLE NOT NULL DEFAULT 48.0 COMMENT '时间衰减半衰期(小时), >0',
`updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='热度评分参数配置表(单行, 管理端动态调整)';

INSERT INTO `score_config` (`likeW`,`commentW`,`collectW`,`shareW`,`dislikeW`,`reportW`,`tauHours`)
VALUES (1.0, 2.0, 3.0, 4.0, -1.0, -5.0, 48.0);

# ------------------------------------------------------------
# 5. 旧库迁移：post 表补 拉踩/举报 计数列（新库已含，无需执行）
# ------------------------------------------------------------
ALTER TABLE `post`
    ADD COLUMN `dislikeCount` INT DEFAULT 0 COMMENT '拉踩(踩)数(热度公式负向降权, 权重默认-1)' AFTER `likeCount`,
    ADD COLUMN `reportCount` INT DEFAULT 0 COMMENT '举报数(热度公式负向重扣, 权重默认-5)' AFTER `dislikeCount`;

# ------------------------------------------------------------
# 12. 用户浏览历史表（去重状态表，upsert 累计；userBehavior 行为流水保持不动）
#     语义：每 (userId, postId) 一行 —— 首次浏览插入(viewCount=1)，再次浏览由
#     ViewHistoryMapper.upsertView 走 ON DUPLICATE KEY UPDATE 累计次数 + 刷新 lastViewAt；
#     「我的浏览历史」列表按 lastViewAt DESC（最近一次浏览优先）查询。
#     游客不写本表（POST /post/{id}/view 内部对未登录直接忽略，也不累加 viewCount）。
# ------------------------------------------------------------
CREATE TABLE `view_history` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键(代码层雪花ASSIGN_ID, DB自增仅兜底)',
  `userId` BIGINT NOT NULL COMMENT '浏览者内部id(=loginId)',
  `postId` BIGINT NOT NULL COMMENT '帖子内部id',
  `viewCount` INT NOT NULL DEFAULT 1 COMMENT '累计浏览次数(每次浏览+1)',
  `lastViewAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次浏览时间',
  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '首次浏览时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ukUserPost` (`userId`,`postId`),
  KEY `idxUserLast` (`userId`,`lastViewAt`),
  KEY `idxPost` (`postId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户浏览历史表(按用户+帖子去重, upsert累计次数)';

# ------------------------------------------------------------
# 13. 帖子收藏表（物理删 toggle；folderId 预留收藏夹分组）
#     语义：folderId=0 表示「默认收藏夹」（Phase 1 未分组，代码写死 0）；
#           用 0 而非 NULL 是因为 MySQL 唯一索引里 NULL 不参与唯一约束（多个 NULL 不冲突），
#           会导致 Phase 1 防重失效。未来做收藏夹时 folder 表 id 从 1 自增，0 留给默认夹，
#           同一帖子可收进多个收藏夹由 ukUserPostFolder 天然支持，无需 ALTER。
#     收藏/取消 = insert/delete（物理删），collectCount 由 CountUtils 原子增减；
#     收藏不通知作者（私密行为，notification type=7 预留但暂不启用）。
# ------------------------------------------------------------
CREATE TABLE `post_collect` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键(代码层雪花ASSIGN_ID, DB自增仅兜底)',
  `postId` BIGINT NOT NULL COMMENT '帖子内部id',
  `userId` BIGINT NOT NULL COMMENT '收藏者内部id(=loginId)',
  `folderId` BIGINT NOT NULL DEFAULT 0 COMMENT '收藏夹id: 0=默认收藏夹(Phase1未分组, 预留列)',
  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `ukUserPostFolder` (`userId`,`postId`,`folderId`),
  KEY `idxUserCreated` (`userId`,`createdAt`),
  KEY `idxPost` (`postId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子收藏表(物理删toggle, folderId预留收藏夹)';

# ------------------------------------------------------------
# 14. 帖子分享流水表（站外分发 / 站内分享，可重复分享无唯一键）
#     语义：channel 与 targetUserId 二选一 ——
#           站外分享 channel=1微信/2朋友圈/3QQ/4微博/5复制链接, targetUserId=NULL, 不通知；
#           站内分享 channel=0, targetUserId=接收者, 通知接收者(type=8 转发/分享, 按天幂等)。
#     分享是离散动作(可重复)，非收藏那种"一人一帖一条"关系，故无唯一键、纯流水。
# ------------------------------------------------------------
CREATE TABLE `post_share` (
  `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键(代码层雪花ASSIGN_ID, DB自增仅兜底)',
  `postId` BIGINT NOT NULL COMMENT '帖子内部id',
  `userId` BIGINT NOT NULL COMMENT '分享者内部id(=loginId)',
  `channel` TINYINT DEFAULT 0 COMMENT '站外分享渠道: 0未知 1微信 2朋友圈 3QQ 4微博 5复制链接',
  `targetUserId` BIGINT DEFAULT NULL COMMENT '站内分享接收者内部id(站外分享为NULL)',
  `createdAt` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '分享时间',
  PRIMARY KEY (`id`),
  KEY `idxPost` (`postId`),
  KEY `idxUserCreated` (`userId`,`createdAt`),
  KEY `idxTarget` (`targetUserId`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子分享流水表(站外分发/站内分享, 可重复分享无唯一键)';

