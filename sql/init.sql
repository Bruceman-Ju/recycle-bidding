-- 回收竞拍系统 - 数据库初始化脚本
-- 首次启动时由 docker-entrypoint-initdb.d 自动执行

CREATE DATABASE IF NOT EXISTS recycle_bidding
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE recycle_bidding;
