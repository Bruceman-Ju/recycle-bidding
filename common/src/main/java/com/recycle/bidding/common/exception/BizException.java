package com.recycle.bidding.common.exception;

/**
 * 业务异常
 * 继承 RuntimeException，用于业务逻辑中的可预期异常场景
 */
public class BizException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 业务异常码
     */
    private final int code;

    /**
     * 构造业务异常
     *
     * @param code    异常码
     * @param message 异常描述
     */
    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通过 ErrorCode 枚举构造业务异常
     *
     * @param errorCode 错误码枚举
     */
    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.code = errorCode.getCode();
    }

    /**
     * 通过 ErrorCode 枚举 + 自定义消息构造业务异常
     *
     * @param errorCode 错误码枚举
     * @param message   自定义异常描述
     */
    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.code = errorCode.getCode();
    }

    public int getCode() {
        return code;
    }

    @Override
    public String toString() {
        return "BizException{" +
                "code=" + code +
                ", message=" + getMessage() +
                '}';
    }
}
