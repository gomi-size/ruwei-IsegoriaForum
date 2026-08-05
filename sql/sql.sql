
# 帖子表
CREATE TABLE `post` (
                        `id` BIGINT NOT NULL AUTO_INCREMENT,
                        `postCode` VARCHAR(32) NOT NULL COMMENT '对外唯一编码',
                        `userId` BIGINT NOT NULL COMMENT '作者',
                        `boardId` BIGINT DEFAULT NULL COMMENT '所属板块',
                        `title` VARCHAR(255) DEFAULT '' COMMENT '标题',
                        `content` TEXT COMMENT '正文(发布时过敏感词 filter)',
                        `cover` VARCHAR(255) DEFAULT '' COMMENT '封面图URL',
                        `type` TINYINT DEFAULT 1 COMMENT '1图文 2视频 3纯文',
                        `videoUrl` VARCHAR(255) DEFAULT '' COMMENT '视频地址',
                        `topic` VARCHAR(64) DEFAULT '' COMMENT '话题',
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
                        `updatedAt` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                        `isDelete` TINYINT DEFAULT 0 COMMENT '逻辑删除: 0未删 1已删(与board表@TableLogic对齐)',
                        PRIMARY KEY (`id`),
                        KEY `idxBoard` (`boardId`,`isTop`,`createdAt`),
                        KEY `idxUser` (`userId`,`createdAt`),
                        KEY `idxStatus` (`status`),
                        KEY `idxTopic` (`topic`),
                        KEY `idxScore` (`score`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='帖子/笔记表(推荐系统物料主表)';

# 可能有多个图片，单独提取出来需要整理出来
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

# 标签
CREATE TABLE `tag` (
                       `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
                       `name` VARCHAR(64) NOT NULL COMMENT '标签名(唯一)',
                       `useCount` INT DEFAULT 0 COMMENT '使用次数(热门标签榜排序依据)',
                       `status` TINYINT DEFAULT 1 COMMENT '1正常 2禁用',
                       PRIMARY KEY (`id`),
                       UNIQUE KEY `ukName` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='标签表';

# 帖子的标签使用
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

# ---- 已建库的增量变更（新建库无需执行）----
 ALTER TABLE `post_image`
   ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '所属帖子版本: 1已发布 2草稿 3审核中 4下架',
   DROP INDEX `idxPost`,
   ADD KEY `idxPostStatus` (`postId`,`status`);
 ALTER TABLE `post_tag`
   ADD COLUMN `status` TINYINT DEFAULT 1 COMMENT '所属帖子版本: 1已发布 2草稿 3审核中 4下架',
   DROP INDEX `ukPostTag`,
   ADD UNIQUE KEY `ukPostTag` (`postId`,`tagId`,`status`);
    ALTER TABLE post DROP INDEX IF EXISTS ukPostCode;


