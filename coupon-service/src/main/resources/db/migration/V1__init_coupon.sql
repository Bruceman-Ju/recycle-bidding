-- 优惠券服务初始化表结构

CREATE TABLE IF NOT EXISTS `coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '券模板ID',
    `name` VARCHAR(100) NOT NULL COMMENT '优惠券名称',
    `description` VARCHAR(500) DEFAULT NULL COMMENT '描述',
    `coupon_type` VARCHAR(32) NOT NULL COMMENT '类型：FIXED_AMOUNT/PERCENTAGE',
    `value` DECIMAL(10,2) NOT NULL COMMENT '优惠值（金额或百分比）',
    `min_spend` DECIMAL(10,2) DEFAULT 0.00 COMMENT '最低消费金额',
    `valid_days` INT DEFAULT 7 COMMENT '有效期天数',
    `status` VARCHAR(16) DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/EXPIRED/DISABLED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='优惠券模板表';

CREATE TABLE IF NOT EXISTS `user_coupon` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户券ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `coupon_id` BIGINT NOT NULL COMMENT '券模板ID',
    `expired_at` DATETIME DEFAULT NULL COMMENT '过期时间',
    `status` VARCHAR(16) DEFAULT 'UNUSED' COMMENT '状态：UNUSED/USED/EXPIRED',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `used_at` DATETIME DEFAULT NULL COMMENT '使用时间',
    PRIMARY KEY (`id`),
    KEY `idx_user_id` (`user_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户优惠券表';
