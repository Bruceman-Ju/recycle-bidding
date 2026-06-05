package com.recycle.bidding.engineer.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 估价计算服务
 *
 * 根据验机评分计算二次估价。
 * 算法：二次估价 = 初始估价 × 各维度评分加权平均值 / 10
 * 权重：外观20%，屏幕30%，功能30%，电池20%
 */
@Slf4j
@Service
public class EvaluationService {

    // 各维度权重
    private static final BigDecimal APPEARANCE_WEIGHT = new BigDecimal("0.20");
    private static final BigDecimal SCREEN_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal FUNCTION_WEIGHT = new BigDecimal("0.30");
    private static final BigDecimal BATTERY_WEIGHT = new BigDecimal("0.20");

    /**
     * 计算二次估价
     *
     * @param initialEstimate  系统初始估价
     * @param appearanceScore  外观评分 (1-10)
     * @param screenScore      屏幕评分 (1-10)
     * @param functionScore    功能评分 (1-10)
     * @param batteryScore     电池评分 (1-10)
     * @return 二次估价金额
     */
    public BigDecimal calculateSecondEstimate(BigDecimal initialEstimate,
                                               Integer appearanceScore,
                                               Integer screenScore,
                                               Integer functionScore,
                                               Integer batteryScore) {
        BigDecimal basePrice = initialEstimate;
        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            basePrice = BigDecimal.ZERO;
        }

        // 计算加权平均分
        BigDecimal weightedScore = BigDecimal.valueOf(appearanceScore).multiply(APPEARANCE_WEIGHT)
                .add(BigDecimal.valueOf(screenScore).multiply(SCREEN_WEIGHT))
                .add(BigDecimal.valueOf(functionScore).multiply(FUNCTION_WEIGHT))
                .add(BigDecimal.valueOf(batteryScore).multiply(BATTERY_WEIGHT));

        // 加权得分归一化到 [0, 1] × basePrice
        BigDecimal ratio = weightedScore.divide(BigDecimal.TEN, 4, RoundingMode.HALF_UP);
        BigDecimal estimate = basePrice.multiply(ratio).setScale(2, RoundingMode.HALF_UP);

        log.info("二次估价计算: basePrice={}, weightedScore={}, estimate={}",
                basePrice, weightedScore, estimate);
        return estimate;
    }
}
