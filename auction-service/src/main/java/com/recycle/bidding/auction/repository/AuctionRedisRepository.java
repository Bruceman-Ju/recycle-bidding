package com.recycle.bidding.auction.repository;

import cn.hutool.core.util.IdUtil;
import com.recycle.bidding.common.constant.AuctionConstants;
import com.recycle.bidding.common.constant.SystemConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 竞拍 Redis 操作封装
 *
 * 使用 Redis ZSET + HASH 存储竞拍数据，保证高并发下性能。
 */
@Repository
@RequiredArgsConstructor
public class AuctionRedisRepository {

    private final RedisTemplate<String, String> redisTemplate;

    private String bidsKey(String auctionId) {
        return AuctionConstants.REDIS_KEY_PREFIX_BIDS + auctionId;
    }

    private String infoKey(String auctionId) {
        return AuctionConstants.REDIS_KEY_PREFIX_INFO + auctionId;
    }

    private String merchantsKey(String auctionId) {
        return AuctionConstants.REDIS_KEY_PREFIX_MERCHANTS + auctionId;
    }

    /**
     * 初始化竞拍：创建ZSET + 设置状态 RUNNING
     * TTL = 竞拍时长(180s) + 缓冲区(300s) = 480秒
     * 给 endAuction() 留出充足的读取窗口，避免数据提前过期
     */
    public void initAuction(String auctionId, BigDecimal basePrice) {
        redisTemplate.opsForHash().putAll(infoKey(auctionId), Map.of(
                "status", AuctionConstants.AUCTION_STATUS_RUNNING,
                "basePrice", basePrice.toPlainString(),
                "currentBidder", "",
                "currentBid", "0"
        ));
        int ttl = SystemConstants.AUCTION_TTL_BUFFER_SECONDS;
        redisTemplate.expire(infoKey(auctionId), Duration.ofSeconds(ttl));
    }

    /**
     * 获取当前最高出价
     */
    public BigDecimal getCurrentBid(String auctionId) {
        String key = bidsKey(auctionId);
        Set<ZSetOperations.TypedTuple<String>> top = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, 0);
        if (top != null && !top.isEmpty()) {
            ZSetOperations.TypedTuple<String> tuple = top.iterator().next();
            return BigDecimal.valueOf(tuple.getScore());
        }
        String basePriceStr = (String) redisTemplate.opsForHash().get(infoKey(auctionId), "basePrice");
        return basePriceStr != null ? new BigDecimal(basePriceStr) : BigDecimal.ZERO;
    }

    /**
     * 获取获胜者
     */
    public String getWinner(String auctionId) {
        String key = bidsKey(auctionId);
        Set<ZSetOperations.TypedTuple<String>> top = redisTemplate.opsForZSet()
                .reverseRangeWithScores(key, 0, 0);
        if (top != null && !top.isEmpty()) {
            ZSetOperations.TypedTuple<String> tuple = top.iterator().next();
            String member = tuple.getValue();
            if (member != null && member.contains(":")) {
                return member.split(":")[0];
            }
        }
        return null;
    }

    /**
     * 获取竞拍中所有出价记录
     */
    public Set<ZSetOperations.TypedTuple<String>> getAllBids(String auctionId) {
        String key = bidsKey(auctionId);
        return redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, -1);
    }

    /**
     * 结束竞拍：标记状态 + 缩短 TTL 等待清理
     */
    public void endAuction(String auctionId) {
        redisTemplate.opsForHash().put(infoKey(auctionId), "status", AuctionConstants.AUCTION_STATUS_ENDED);
        redisTemplate.expire(infoKey(auctionId), Duration.ofMinutes(10));
    }

    /**
     * 完全清理竞拍数据（endAuction 结算完成后主动调用）
     * 不等 TTL 自然过期，及时释放 Redis 内存
     */
    public void cleanupAuctionData(String auctionId) {
        redisTemplate.delete(bidsKey(auctionId));
        redisTemplate.delete(infoKey(auctionId));
        redisTemplate.delete(merchantsKey(auctionId));
    }

    /**
     * 商户加入竞拍
     */
    public void addAuctionMerchant(String auctionId, Long merchantId) {
        redisTemplate.opsForSet().add(merchantsKey(auctionId), String.valueOf(merchantId));
        redisTemplate.expire(merchantsKey(auctionId), Duration.ofHours(1));
    }

    /**
     * 获取参与竞拍的商户列表
     */
    public List<Long> getAuctionMerchants(String auctionId) {
        Set<String> members = redisTemplate.opsForSet().members(merchantsKey(auctionId));
        if (members == null) return List.of();
        return members.stream().map(Long::parseLong).collect(Collectors.toList());
    }

    /**
     * 存储关联的 orderId（endAuction 落盘时需要）
     */
    public void setAuctionOrderId(String auctionId, Long orderId) {
        redisTemplate.opsForHash().put(infoKey(auctionId), "orderId", String.valueOf(orderId));
    }

    /**
     * 获取关联的 orderId
     */
    public Long getAuctionOrderId(String auctionId) {
        String val = (String) redisTemplate.opsForHash().get(infoKey(auctionId), "orderId");
        return val != null ? Long.parseLong(val) : null;
    }

    /**
     * 获取竞拍状态
     */
    public String getAuctionStatus(String auctionId) {
        return (String) redisTemplate.opsForHash().get(infoKey(auctionId), "status");
    }

    /**
     * 获取出价锁（分布式锁，Layer 1 幂等）
     * <p>
     * 使用 Redis SET NX EX 实现，防止同一商户在同一竞拍中并发出价。
     * TTL 设置为竞拍时长 + 额外余量，确保竞拍期间出价不会因为锁重入而产生重复。
     * 锁过期后商户可再次出价，由 Lua 脚本的业务约束（Layer 2）兜底。
     *
     * @param auctionId  竞拍ID
     * @param merchantId 商户ID
     * @return true = 首次获取锁（允许出价）, false = 已有出价在处理中
     */
    public boolean acquireBidLock(String auctionId, Long merchantId) {
        String lockKey = AuctionConstants.REDIS_KEY_PREFIX_LOCK + auctionId + ":" + merchantId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "1", Duration.ofSeconds(SystemConstants.AUCTION_DURATION_SECONDS));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 删除竞拍数据
     */
    public void deleteAuction(String auctionId) {
        redisTemplate.delete(bidsKey(auctionId));
        redisTemplate.delete(infoKey(auctionId));
        redisTemplate.delete(merchantsKey(auctionId));
    }

    /**
     * 获取所有状态为 RUNNING 的活跃竞拍 ID
     *
     * 扫描 Redis 中所有 auction:info:* 的 HASH key，
     * 只返回 status 字段为 RUNNING 的竞拍。
     */
    public List<String> getActiveAuctionIds() {
        String pattern = AuctionConstants.REDIS_KEY_PREFIX_INFO + "*";
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys == null || keys.isEmpty()) {
            return List.of();
        }

        List<String> activeIds = new java.util.ArrayList<>();
        for (String key : keys) {
            String status = (String) redisTemplate.opsForHash().get(key, "status");
            if (AuctionConstants.AUCTION_STATUS_RUNNING.equals(status)) {
                // key = "auction:info:AUC123" → 取最后一段
                String auctionId = key.substring(AuctionConstants.REDIS_KEY_PREFIX_INFO.length());
                activeIds.add(auctionId);
            }
        }
        return activeIds;
    }
}
