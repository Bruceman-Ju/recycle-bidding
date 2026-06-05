package com.recycle.bidding.order.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.recycle.bidding.common.constant.OrderStatus;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import com.recycle.bidding.common.result.PageResult;
import com.recycle.bidding.common.util.TraceIdUtil;
import com.recycle.bidding.order.entity.Order;
import com.recycle.bidding.order.entity.OrderEventLog;
import com.recycle.bidding.order.entity.OrderTask;
import com.recycle.bidding.order.repository.OrderEventLogRepository;
import com.recycle.bidding.order.repository.OrderRepository;
import com.recycle.bidding.order.repository.OrderTaskRepository;
import com.recycle.bidding.order.service.OrderEventPublisher;
import com.recycle.bidding.order.service.OrderService;
import com.recycle.bidding.order.service.OrderStateMachine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderEventLogRepository orderEventLogRepository;
    private final OrderStateMachine orderStateMachine;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderTaskRepository orderTaskRepository;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order createOrder(Long userId, Long phoneModelId, BigDecimal initialEstimate) {
        String orderNo = "ORD" + IdUtil.getSnowflakeNextIdStr();
        Order order = Order.builder()
                .orderNo(orderNo)
                .userId(userId)
                .phoneModelId(phoneModelId)
                .initialEstimate(initialEstimate)
                .version(1)
                .status(OrderStatus.PENDING_EVALUATION)
                .build();
        orderRepository.insert(order);

        // 记录事件日志
        OrderEventLog eventLog = OrderEventLog.builder()
                .orderId(order.getId())
                .fromStatus(null)
                .toStatus(OrderStatus.PENDING_EVALUATION)
                .operator("SYSTEM")
                .operatorId(userId)
                .remark("用户下单，初始估价：" + initialEstimate)
                .traceId(TraceIdUtil.getTraceId())
                .build();
        orderEventLogRepository.insert(eventLog);

        // 同步创建工程师任务（同库同事务，无需分布式事务）
        // engineer_id 在创建时为 NULL，工程师调用 acceptTask 后填充
        OrderTask task = OrderTask.builder()
                .orderId(order.getId())
                .engineerId(null)
                .taskStatus("ASSIGNED")
                .assignedAt(LocalDateTime.now())
                .build();
        orderTaskRepository.insert(task);

        // 发送MQ消息，为了推送服务。
        orderEventPublisher.publishOrderCreated(order);

        log.info("订单创建成功: orderId={}, orderNo={}", order.getId(), order.getOrderNo());
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order confirmOrder(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 校验幂等
        if (OrderStatus.USER_CONFIRMED.equals(order.getStatus())) {
            log.warn("重复确认估价: orderId={}", orderId);
            return order;
        }

        // 状态机校验：当前必须是 SECOND_EVALUATED
        orderStateMachine.validateTransition(order.getStatus(), OrderStatus.USER_CONFIRMED);

        // 更新状态
        order.setStatus(OrderStatus.USER_CONFIRMED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.updateById(order);

        // 记录事件日志
        OrderEventLog eventLog = OrderEventLog.builder()
                .orderId(orderId)
                .fromStatus(OrderStatus.SECOND_EVALUATED)
                .toStatus(OrderStatus.USER_CONFIRMED)
                .operator("USER")
                .operatorId(order.getUserId())
                .remark("用户确认估价")
                .traceId(TraceIdUtil.getTraceId())
                .build();
        orderEventLogRepository.insert(eventLog);

        // 发布状态变更事件
        orderEventPublisher.publishStatusChanged(order, eventLog);

        log.info("用户确认估价: orderId={}", orderId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order confirmPayment(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        // 状态机校验：当前必须是 AUCTION_ENDED
        orderStateMachine.validateTransition(order.getStatus(), OrderStatus.PAYMENT_COMPLETED);

        // 更新状态
        order.setStatus(OrderStatus.PAYMENT_COMPLETED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.updateById(order);

        // 记录事件日志
        OrderEventLog eventLog = OrderEventLog.builder()
                .orderId(orderId)
                .fromStatus(OrderStatus.AUCTION_ENDED)
                .toStatus(OrderStatus.PAYMENT_COMPLETED)
                .operator("ENGINEER")
                .operatorId(null)
                .remark("工程师确认打款")
                .traceId(TraceIdUtil.getTraceId())
                .build();
        orderEventLogRepository.insert(eventLog);

        orderEventPublisher.publishStatusChanged(order, eventLog);

        log.info("工程师确认打款: orderId={}", orderId);
        return order;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order updateOrderStatus(Long orderId, String fromStatus, String toStatus, String operator, Long operatorId, String remark) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        orderStateMachine.validateTransition(fromStatus, toStatus);

        order.setStatus(toStatus);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.updateById(order);

        OrderEventLog eventLog = OrderEventLog.builder()
                .orderId(orderId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .operator(operator)
                .operatorId(operatorId)
                .remark(remark)
                .traceId(TraceIdUtil.getTraceId())
                .build();
        orderEventLogRepository.insert(eventLog);

        orderEventPublisher.publishStatusChanged(order, eventLog);

        return order;
    }

    @Override
    public Order getOrderById(Long orderId) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }
        return order;
    }

    @Override
    public PageResult<Order> getUserOrders(Long userId, int page, int size) {
        LambdaQueryWrapper<Order> wrapper = new LambdaQueryWrapper<Order>()
                .eq(Order::getUserId, userId)
                .orderByDesc(Order::getCreatedAt);

        IPage<Order> result = orderRepository.selectPage(new Page<>(page, size), wrapper);
        return new PageResult<>(result.getTotal(), (int) result.getCurrent(), (int) result.getSize(), result.getRecords());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Order updateOrderToAuctionEnded(Long orderId, Long winnerMerchantId, BigDecimal finalPrice) {
        Order order = orderRepository.selectById(orderId);
        if (order == null) {
            throw new BizException(ErrorCode.ORDER_NOT_FOUND);
        }

        orderStateMachine.validateTransition(order.getStatus(), OrderStatus.AUCTION_ENDED);

        order.setStatus(OrderStatus.AUCTION_ENDED);
        order.setWinnerMerchantId(winnerMerchantId);
        order.setFinalPrice(finalPrice);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.updateById(order);

        OrderEventLog eventLog = OrderEventLog.builder()
                .orderId(orderId)
                .fromStatus(OrderStatus.AUCTIONING)
                .toStatus(OrderStatus.AUCTION_ENDED)
                .operator("SYSTEM")
                .operatorId(null)
                .remark("竞拍结束，胜出商户ID=" + winnerMerchantId + "，最终价格=" + finalPrice)
                .traceId(TraceIdUtil.getTraceId())
                .build();
        orderEventLogRepository.insert(eventLog);

        orderEventPublisher.publishStatusChanged(order, eventLog);

        return order;
    }
}
