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
                        `score` DECIMAL(12,4) DEFAULT 0 COMMENT '预计算热度分: (赞×1+评×2+藏×3+享×4)×exp(-Δt/τ); 热点召回/粗排直接ORDER BY score DESC',
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

# 1. board 表：下划线列 → 驼峰列（与 Board 实体字段对齐）
ALTER TABLE `board`
    RENAME COLUMN `creator_id` TO `creatorId`,
    RENAME COLUMN `follow_count` TO `followCount`,
    RENAME COLUMN `post_count` TO `postCount`,
    RENAME COLUMN `level_rule` TO `levelRule`,
    RENAME COLUMN `created_at` TO `createdAt`,
    RENAME COLUMN `updated_at` TO `updatedAt`;

# 2. board_follow 表：下划线列 → 驼峰列 + 补 status
ALTER TABLE `board_follow`
    RENAME COLUMN `created_at` TO `createdAt`,
    ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '关注状态: 1关注 2已取消关注(软标记保留历史)' AFTER `boardId`;

# 3. post 表旧库补 draftOfId（草稿槽位）
ALTER TABLE `post`
    ADD COLUMN `draftOfId` BIGINT DEFAULT NULL COMMENT '草稿来源帖id(null=新建草稿)' AFTER `isDelete`,
    ADD KEY `idxDraft` (`userId`,`draftOfId`);
