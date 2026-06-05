package com.recycle.bidding.gateway.handler;

import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 熔断降级处理器
 *
 * 当微服务熔断时，返回友好的降级提示。
 */
@RestController
public class FallbackController {

    @GetMapping("/fallback/order")
    public Result<String> orderFallback() {
        return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "订单服务暂不可用，请稍后重试");
    }

    @GetMapping("/fallback/engineer")
    public Result<String> engineerFallback() {
        return Result.fail(ErrorCode.SYSTEM_ERROR.getCode(), "工程师服务暂不可用，请稍后重试");
    }
}
