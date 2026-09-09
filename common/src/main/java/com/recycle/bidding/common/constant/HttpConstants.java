package com.recycle.bidding.common.constant;

/*
 * HTTP 常量定义
 */
public class HttpConstants {

    /**
     * 全链路追踪 ID 请求头
     */
    public static final String TRACE_ID_HEADER = "x-trace-id";

    /**
     * 鉴权 Token 请求头
     */
    public static final String AUTH_HEADER = "Authorization";

    //  分页默认值
    /**
     * 默认每页大小
     */
    public static final String DEFAULT_PAGE_SIZE = "20";

    /**
     * 默认页码
     */
    public static final String DEFAULT_PAGE = "1";

    /**
     * 私有构造函数，防止实例化
     */
    private HttpConstants() {}
}
