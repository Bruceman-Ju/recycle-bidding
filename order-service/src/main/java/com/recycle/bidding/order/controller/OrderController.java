package com.recycle.bidding.order.controller;

import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.common.result.Result;
import com.recycle.bidding.order.dto.CreateOrderRequest;
import com.recycle.bidding.order.entity.Order;
import com.recycle.bidding.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    /**
     * 创建订单
     */
    @PostMapping("/create")
    public Result<Order> createOrder(@Valid @RequestBody CreateOrderRequest req) {
        Order order = orderService.createOrder(req.getUserId(), req.getPhoneModelId(), req.getInitialEstimate());
        return Result.ok(order);
    }

    /**
     * 用户确认估价
     */
    @PostMapping("/{orderId}/confirm")
    public Result<Order> confirmOrder(@PathVariable Long orderId) {
        Order order = orderService.confirmOrder(orderId);
        return Result.ok(order);
    }

    /**
     * 工程师确认打款
     */
    @PostMapping("/{orderId}/confirm-payment")
    public Result<Order> confirmPayment(@PathVariable Long orderId) {
        Order order = orderService.confirmPayment(orderId);
        return Result.ok(order);
    }

    /**
     * 查询订单详情
     */
    @GetMapping("/{orderId}")
    public Result<Order> getOrder(@PathVariable Long orderId) {
        Order order = orderService.getOrderById(orderId);
        return Result.ok(order);
    }

    /**
     * 查询用户订单列表
     */
    @GetMapping("/user/{userId}")
    public Result<PageResult<Order>> getUserOrders(@PathVariable Long userId,
                                                   @RequestParam(defaultValue = "1") int page,
                                                   @RequestParam(defaultValue = "20") int size) {
        PageResult<Order> result = orderService.getUserOrders(userId, page, size);
        return Result.ok(result);
    }
}
