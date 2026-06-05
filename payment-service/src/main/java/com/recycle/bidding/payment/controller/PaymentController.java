package com.recycle.bidding.payment.controller;

import com.recycle.bidding.common.result.Result;
import com.recycle.bidding.payment.dto.CreatePaymentRequest;
import com.recycle.bidding.payment.entity.PaymentRecord;
import com.recycle.bidding.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * 创建支付
     */
    @PostMapping("/create")
    public Result<PaymentRecord> createPayment(@Valid @RequestBody CreatePaymentRequest req) {
        PaymentRecord record = paymentService.createPayment(req.getOrderId(), req.getPayerId(), req.getPayeeId(), req.getAmount());
        return Result.ok(record);
    }

    /**
     * 执行支付
     */
    @PostMapping("/{paymentId}/process")
    public Result<PaymentRecord> processPayment(@PathVariable Long paymentId) {
        PaymentRecord record = paymentService.processPayment(paymentId);
        return Result.ok(record);
    }

    /**
     * 查询支付状态
     */
    @GetMapping("/{paymentId}")
    public Result<PaymentRecord> getPayment(@PathVariable Long paymentId) {
        PaymentRecord record = paymentService.getPaymentById(paymentId);
        return Result.ok(record);
    }
}
