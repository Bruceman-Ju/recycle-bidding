package com.recycle.bidding.order.service;

import com.recycle.bidding.common.constant.OrderStatus;
import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订单状态机校验服务
 *
 * 定义状态流转规则，提供状态变更合法性校验。
 */
@Service
public class OrderStateMachine {

    /**
     * 状态流转映射表：当前状态 → 允许的下一个状态列表
     */
    private static final Map<String, List<String>> TRANSITION_MAP = new ConcurrentHashMap<>();

    static {
        // PENDING_EVALUATION
        TRANSITION_MAP.put(OrderStatus.PENDING_EVALUATION, List.of(
                OrderStatus.ENGINEER_ASSIGNED,
                OrderStatus.ORDER_CLOSED
        ));
        // ENGINEER_ASSIGNED
        TRANSITION_MAP.put(OrderStatus.ENGINEER_ASSIGNED, List.of(
                OrderStatus.INSPECTING,
                OrderStatus.ORDER_CLOSED
        ));
        // INSPECTING
        TRANSITION_MAP.put(OrderStatus.INSPECTING, List.of(
                OrderStatus.SECOND_EVALUATED,
                OrderStatus.ORDER_CLOSED
        ));
        // SECOND_EVALUATED
        TRANSITION_MAP.put(OrderStatus.SECOND_EVALUATED, List.of(
                OrderStatus.USER_CONFIRMED,
                OrderStatus.ORDER_CLOSED
        ));
        // USER_CONFIRMED
        TRANSITION_MAP.put(OrderStatus.USER_CONFIRMED, List.of(
                OrderStatus.AUCTIONING
        ));
        // AUCTIONING
        TRANSITION_MAP.put(OrderStatus.AUCTIONING, List.of(
                OrderStatus.AUCTION_ENDED,
                OrderStatus.AUCTION_FAILED,
                OrderStatus.ORDER_CLOSED
        ));
        // AUCTION_ENDED
        TRANSITION_MAP.put(OrderStatus.AUCTION_ENDED, List.of(
                OrderStatus.PAYMENT_COMPLETED
        ));
        // AUCTION_FAILED — 宕机或数据不一致，工程师可重试或关闭订单
        TRANSITION_MAP.put(OrderStatus.AUCTION_FAILED, List.of(
                OrderStatus.AUCTIONING,
                OrderStatus.ORDER_CLOSED
        ));
        // PAYMENT_COMPLETED
        TRANSITION_MAP.put(OrderStatus.PAYMENT_COMPLETED, List.of(
                OrderStatus.ORDER_COMPLETED,
                OrderStatus.ORDER_CLOSED
        ));
        // ORDER_COMPLETED — 终态，无后续流转
        TRANSITION_MAP.put(OrderStatus.ORDER_COMPLETED, List.of());
        // ORDER_CLOSED — 终态，无后续流转
        TRANSITION_MAP.put(OrderStatus.ORDER_CLOSED, List.of());
    }

    /**
     * 校验状态变更是否合法
     *
     * @param fromStatus 当前状态
     * @param toStatus   目标状态
     * @throws BizException 如果状态变更不被允许
     */
    public void validateTransition(String fromStatus, String toStatus) {
        List<String> allowedTargets = TRANSITION_MAP.get(fromStatus);
        if (allowedTargets == null) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID.getCode(),
                    "未知的状态: " + fromStatus);
        }
        if (!allowedTargets.contains(toStatus)) {
            throw new BizException(ErrorCode.ORDER_STATUS_INVALID.getCode(),
                    "不允许的状态变更: " + fromStatus + " → " + toStatus);
        }
    }

    /**
     * 获取当前状态下允许的所有目标状态
     */
    public List<String> getAllowedTargets(String status) {
        return TRANSITION_MAP.getOrDefault(status, List.of());
    }
}
