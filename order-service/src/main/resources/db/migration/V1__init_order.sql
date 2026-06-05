-- 订单服务初始化表结构

CREATE TABLE IF NOT EXISTS `orders` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    `order_no` VARCHAR(32) NOT NULL COMMENT '订单编号',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `phone_model_id` BIGINT DEFAULT NULL COMMENT '手机型号ID',
    `initial_estimate` DECIMAL(10,2) DEFAULT NULL COMMENT '系统初始估价',
    `second_estimate` DECIMAL(10,2) DEFAULT NULL COMMENT '工程师二次估价',
    `final_price` DECIMAL(10,2) DEFAULT NULL COMMENT '最终成交价',
    `winner_merchant_id` BIGINT DEFAULT NULL COMMENT '胜出商户ID',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING_EVALUATION' COMMENT '订单状态',
    `version` INT DEFAULT 1 COMMENT '乐观锁版本号',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted` TINYINT DEFAULT 0 COMMENT '逻辑删除标记',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_order_no` (`order_no`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

CREATE TABLE IF NOT EXISTS `order_event_log` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `order_id` BIGINT NOT NULL COMMENT '订单ID',
    `from_status` VARCHAR(32) DEFAULT NULL COMMENT '变更前状态',
    `to_status` VARCHAR(32) NOT NULL COMMENT '变更后状态',
    `operator` VARCHAR(50) DEFAULT NULL COMMENT '操作人(SYSTEM/USER/ENGINEER/MERCHANT)',
    `operator_id` BIGINT DEFAULT NULL COMMENT '操作人ID',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    `trace_id` VARCHAR(64) DEFAULT NULL COMMENT '全链路追踪ID',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单事件日志表';
