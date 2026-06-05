package com.recycle.bidding.common.enums;

/**
 * 竞拍状态枚举
 */
public enum AuctionStatusEnum {

    /**
     * 待开始
     */
    PENDING("PENDING", "待开始"),

    /**
     * 进行中
     */
    RUNNING("RUNNING", "进行中"),

    /**
     * 已结束
     */
    ENDED("ENDED", "已结束"),

    /**
     * 已关闭（异常终止）
     */
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String description;

    AuctionStatusEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据code获取枚举
     */
    public static AuctionStatusEnum fromCode(String code) {
        for (AuctionStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断是否活跃状态（可出价）
     */
    public boolean isActive() {
        return this == RUNNING;
    }

    /**
     * 判断是否已结束
     */
    public boolean isFinished() {
        return this == ENDED || this == CLOSED;
    }
}
