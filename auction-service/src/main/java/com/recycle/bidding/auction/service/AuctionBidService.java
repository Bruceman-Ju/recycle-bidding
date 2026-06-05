package com.recycle.bidding.auction.service;

import com.recycle.bidding.common.exception.BizException;
import com.recycle.bidding.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Lua 出价脚本加载和执行服务
 *
 * 保证出价检查+更新的原子性操作。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuctionBidService {

    private final RedisTemplate<String, String> redisTemplate;

    private DefaultRedisScript<Long> bidScript;

    @PostConstruct
    public void init() {
        bidScript = new DefaultRedisScript<>();
        bidScript.setLocation(new ClassPathResource("scripts/bid.lua"));
        bidScript.setResultType(Long.class);
        log.info("Lua 出价脚本加载完成");
    }

    /**
     * 执行原子出价（盲拍模式）
     * <p>
     * 每个商户每场竞拍最多出价 2 次，由 Lua 脚本在 Redis 内原子判断。
     *
     * @param auctionId  竞拍ID
     * @param merchantId 商户ID
     * @param bidPrice   出价金额
     * @return 1=成功, -1=竞拍未进行中, -2=出价过低, -3=超出最大出价次数
     */
    public long executeBid(String auctionId, Long merchantId, BigDecimal bidPrice) {
        String bidsKey = "auction:bids:" + auctionId;
        String infoKey = "auction:info:" + auctionId;
        String bidCountKey = "auction:bidcount:" + auctionId + ":" + merchantId;

        List<String> keys = Arrays.asList(bidsKey, infoKey, bidCountKey);

        String merchantIdStr = String.valueOf(merchantId);
        String bidPriceStr = bidPrice.toPlainString();
        String timestamp = String.valueOf(System.currentTimeMillis());

        Long result = redisTemplate.execute(bidScript, keys,
                merchantIdStr, bidPriceStr, timestamp);

        if (result.equals(-1L)) {
            log.error("Lua脚本竞拍失败: auctionId={}, merchantId={}", auctionId, merchantId);
            throw new BizException(ErrorCode.SYSTEM_ERROR.getCode(), "出价脚本执行异常");
        }

        return result;
    }
}
