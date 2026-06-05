-- 工程师服务初始化表结构

CREATE TABLE IF NOT EXISTS `engineer` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工程师ID',
    `name` VARCHAR(50) NOT NULL COMMENT '姓名',
    `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号',
    `status` TINYINT DEFAULT 1 COMMENT '状态：0-休息，1-空闲，2-忙碌',
    `total_tasks` INT DEFAULT 0 COMMENT '累计接单数',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工程师表';

CREATE TABLE IF NOT EXISTS `engineer_task` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '任务ID',
    `engineer_id` BIGINT DEFAULT NULL COMMENT '工程师ID（下单时为空，工程师接单后填充）',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `task_status` VARCHAR(32) NOT NULL COMMENT '任务状态：ASSIGNED/ACCEPTED/INSPECTING/COMPLETED',
    `assigned_at` DATETIME DEFAULT NULL COMMENT '分配时间',
    `accepted_at` DATETIME DEFAULT NULL COMMENT '接单时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_order_id` (`order_id`),
    KEY `idx_engineer_id` (`engineer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工程师任务表';

CREATE TABLE IF NOT EXISTS `evaluation_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `engineer_id` BIGINT NOT NULL COMMENT '工程师ID',
    `appearance_score` TINYINT DEFAULT NULL COMMENT '外观评分 (1-10)',
    `screen_score` TINYINT DEFAULT NULL COMMENT '屏幕评分 (1-10)',
    `function_score` TINYINT DEFAULT NULL COMMENT '功能评分 (1-10)',
    `battery_score` TINYINT DEFAULT NULL COMMENT '电池评分 (1-10)',
    `overall_condition` VARCHAR(20) DEFAULT NULL COMMENT '整体成色：NEW/LIKE_NEW/GOOD/FAIR/POOR',
    `second_estimate` DECIMAL(10,2) DEFAULT NULL COMMENT '计算出的二次估价',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注描述',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_engineer_id` (`engineer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='验机记录表';
