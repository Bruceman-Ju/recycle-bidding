package com.recycle.bidding.common.result;

import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.util.TraceIdUtil;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 统一响应体
 */
@Data
public class Result<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 业务码
     */
    private int code;

    /**
     * 描述
     */
    private String message;

    /**
     * 响应数据
     */
    private T data;

    /**
     * 全链路追踪ID
     */
    private String traceId;

    public Result() {
    }

    public Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = TraceIdUtil.getTraceId();
    }

    public Result(int code, String message, T data, String traceId) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.traceId = traceId;
    }

    /**
     * 成功响应（无数据）
     */
    public static <T> Result<T> ok() {
        return new Result<>(0, "success", null);
    }

    /**
     * 成功响应（带数据）
     */
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "success", data);
    }

    /**
     * 失败响应
     */
    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null);
    }

    /**
     * 失败响应（通过ErrorCode）
     */
    public static <T> Result<T> fail(ErrorCode errorCode) {
        return new Result<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    // --- Getters and Setters ---

    @Override
    public String toString() {
        return "Result{" +
                "code=" + code +
                ", message='" + message + '\'' +
                ", data=" + data +
                ", traceId='" + traceId + '\'' +
                '}';
    }
}
