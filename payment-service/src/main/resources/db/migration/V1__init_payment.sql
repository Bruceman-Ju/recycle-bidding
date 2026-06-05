-- 支付服务初始化表结构

CREATE TABLE IF NOT EXISTS `payment_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '支付记录ID',
    `payment_no` VARCHAR(32) NOT NULL COMMENT '支付流水号',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `payer_id` BIGINT NOT NULL COMMENT '付款方ID',
    `payee_id` BIGINT NOT NULL COMMENT '收款方ID',
    `platform_fee` DECIMAL(10,2) DEFAULT 0.00 COMMENT '平台手续费',
    `amount` DECIMAL(10,2) NOT NULL COMMENT '支付金额',
    `payment_type` VARCHAR(32) NOT NULL COMMENT '支付类型：MERCHANT_PLATFORM/PLATFORM_USER',
    `status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '支付状态：PENDING/PROCESSING/SUCCESS/FAILED',
    `transaction_id` VARCHAR(64) DEFAULT NULL COMMENT '第三方支付交易号',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completed_at` DATETIME DEFAULT NULL COMMENT '完成时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_payment_no` (`payment_no`),
    KEY `idx_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付记录表';
