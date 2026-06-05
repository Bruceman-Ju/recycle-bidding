-- 竞拍服务初始化表结构

CREATE TABLE IF NOT EXISTS `auction_record` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    `order_id` BIGINT NOT NULL COMMENT '关联订单ID',
    `auction_id` VARCHAR(32) NOT NULL COMMENT '竞拍ID',
    `merchant_id` BIGINT NOT NULL COMMENT '商户ID',
    `bid_price` DECIMAL(10,2) NOT NULL COMMENT '出价金额',
    `bid_time` DATETIME(3) NOT NULL COMMENT '出价时间',
    `is_winner` TINYINT DEFAULT 0 COMMENT '是否胜出：0-否，1-是',
    `source` VARCHAR(16) DEFAULT 'redis' COMMENT '数据来源：redis/mysql',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `idx_auction_id` (`auction_id`),
    KEY `idx_order_id` (`order_id`),
    KEY `idx_merchant_auction` (`merchant_id`, `auction_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='竞拍记录表';
