-- 竞拍出价原子脚本（盲拍模式）
-- KEYS[1] = auction:bids:{auctionId} (ZSET key)
-- KEYS[2] = auction:info:{auctionId} (HASH key)
-- KEYS[3] = auction:bidcount:{auctionId}:{merchantId} (STRING counter key)
-- ARGV[1] = merchantId
-- ARGV[2] = bidPrice
-- ARGV[3] = currentTimestamp
-- 返回值: 1=成功, -1=竞拍未进行中, -2=出价过低, -3=超出最大出价次数

local status = redis.call('HGET', KEYS[2], 'status')
if status ~= 'RUNNING' then
    return -1
end

-- 检查商户出价次数（限流：最多 2 次）
local bidCount = tonumber(redis.call('GET', KEYS[3]) or 0)
if bidCount >= 2 then
    return -3
end

-- 获取底价与当前最高价，校验出价金额
local basePrice = tonumber(redis.call('HGET', KEYS[2], 'basePrice')) or 0
local topBid = redis.call('ZREVRANGE', KEYS[1], 0, 0, 'WITHSCORES')
local currentTop = 0
if #topBid >= 2 then
    currentTop = tonumber(topBid[2])
end

local minPrice = currentTop > 0 and currentTop or basePrice
if tonumber(ARGV[2]) <= minPrice then
    return -2
end

-- 写入 ZSET 并更新当前最高价
redis.call('ZADD', KEYS[1], ARGV[2], ARGV[1] .. ':' .. ARGV[3])
redis.call('HSET', KEYS[2], 'currentBid', ARGV[2], 'currentBidder', ARGV[1])

-- 递增出价计数，过期时间与竞拍保持一致
redis.call('INCR', KEYS[3])
redis.call('EXPIRE', KEYS[3], 300)

return 1
