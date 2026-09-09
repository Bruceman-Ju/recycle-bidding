package com.recycle.bidding.common.exception;

import lombok.Getter;

/**
 * 异常码枚举定义
 * 码段分配：
 *   0      = 成功
 *   10xxx  = 订单服务
 *   20xxx  = 竞拍服务
 *   30xxx  = 支付服务
 *   40xxx  = 优惠券服务
 *   50xxx  = 工程师服务
 *   60xxx  = 商户服务
 *   90xxx  = 网关
 *   99xxx  = 系统
 */
@Getter
public enum ErrorCode {

    //  成功 
    SUCCESS(0, "success"),

    //  订单服务 10xxx 
    ORDER_NOT_FOUND(10001, "订单不存在"),
    ORDER_STATUS_INVALID(10002, "订单状态不允许操作"),
    ORDER_IDEMPOTENT_CONFLICT(10003, "重复操作"),

    //  竞拍服务 20xxx 
    AUCTION_NOT_FOUND(20001, "竞拍不存在"),
    AUCTION_ENDED(20002, "竞拍已结束"),
    BID_PRICE_TOO_LOW(20003, "出价过低"),
    BID_DUPLICATE(20004, "重复出价"),
    BID_MAX_EXCEEDED(20005, "已达到最大出价次数"),
    BID_PROCESSING(20006, "出价正在处理中，请勿重复提交"),

    //  支付服务 30xxx 
    PAYMENT_NOT_FOUND(30001, "支付记录不存在"),
    PAYMENT_FAILED(30002, "支付失败"),

    //  优惠券服务 40xxx 
    COUPON_NOT_FOUND(40001, "优惠券不存在"),
    COUPON_EXPIRED(40002, "优惠券已过期"),

    //  系统服务 99xxx 
    SYSTEM_ERROR(99999, "系统繁忙");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据异常码查找枚举
     */
    public static ErrorCode fromCode(int code) {
        for (ErrorCode errorCode : values()) {
            if (errorCode.code == code) {
                return errorCode;
            }
        }
        return SYSTEM_ERROR;
    }
}
