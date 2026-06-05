# API 文档

## 通用说明

### 统一前缀
```
/api/v1/{service-name}
```

### 统一响应格式
```json
{
  "code": 0,           // 0=成功，非0=错误
  "message": "success",
  "data": {},          // 业务数据
  "traceId": "xxx"     // 全链路追踪ID
}
```

### 鉴权方式
请求头: `Authorization: Bearer mock-token-{userId}`

### 公共错误码
| code | 说明 |
|------|------|
| 0 | 成功 |
| 1001 | 参数错误 |
| 2001 | 订单不存在 |
| 2002 | 订单状态不合法 |
| 9999 | 系统内部错误 |

---

## 订单服务 (order-service) — 端口 8082

### 创建订单
```
POST /api/v1/order/create
Params: userId, phoneModelId, initialEstimate
Response: { code:0, data: { id, orderNo, status:"PENDING_EVALUATION", ... } }
```

### 确认估价
```
POST /api/v1/order/{orderId}/confirm
Params: idempotentKey
Response: { code:0, data: { id, status:"USER_CONFIRMED", ... } }
```

### 确认打款
```
POST /api/v1/order/{orderId}/confirm-payment
Response: { code:0, data: { id, status:"PAYMENT_COMPLETED", ... } }
```

### 查询订单详情
```
GET /api/v1/order/{orderId}
Response: { code:0, data: { id, orderNo, status, ... } }
```

### 查询用户订单列表
```
GET /api/v1/order/user/{userId}?page=1&size=20
Response: { code:0, data: { total, page, size, records: [...] } }
```

---

## 工程师服务 (engineer-service) — 端口 8083

### 工程师接单
```
POST /api/v1/engineer/task/accept
Params: taskId, engineerId
Response: { code:0, data: { id, taskStatus:"ACCEPTED", ... } }
```

### 开始验机
```
PUT /api/v1/engineer/task/{taskId}/start-inspect
Response: { code:0, data: { id, taskStatus:"INSPECTING", ... } }
```

### 提交验机记录
```
POST /api/v1/engineer/evaluation/submit
Params: orderId, engineerId, initialEstimate, appearanceScore(1-10), screenScore(1-10), functionScore(1-10), batteryScore(1-10), remark(可选)
Response: { code:0, data: { id, secondEstimate, ... } }
```

---

## 竞拍服务 (auction-service) — 端口 8085

### 发起竞拍
```
POST /api/v1/auction/start
Params: orderId, basePrice
Response: { code:0, data: { auctionId, status:"RUNNING", ... } }
```

### 商户出价
```
POST /api/v1/auction/bid
Params: auctionId, merchantId, bidPrice, idempotentKey(可选)
Response: { code:0, data: { id, auctionId, merchantId, bidPrice, ... } }
```

### 查询竞拍状态
```
GET /api/v1/auction/{auctionId}
Response: { code:0, data: { auctionId, status, currentBid, winnerMerchantId, participatingMerchants } }
```

### 商户加入竞拍
```
POST /api/v1/auction/{auctionId}/join
Params: merchantId
Response: { code:0 }
```

### 查询活跃竞拍
```
GET /api/v1/auction/active
Response: { code:0, data: [...] }
```

---

## 支付服务 (payment-service) — 端口 8088

### 创建支付
```
POST /api/v1/payment/create
Params: orderId, payerId, payeeId, amount
Response: { code:0, data: { id, paymentNo, status:"PENDING", ... } }
```

### 执行支付
```
POST /api/v1/payment/{paymentId}/process
Response: { code:0, data: { id, status:"SUCCESS", transactionId, ... } }
```

### 查询支付状态
```
GET /api/v1/payment/{paymentId}
Response: { code:0, data: { id, paymentNo, status, ... } }
```

---

## 商户服务 (merchant-service) — 端口 8084

### 创建商户
```
POST /api/v1/merchant
Params: companyName, contactName, phone
Response: { code:0, data: { id, companyName, ... } }
```

### 查询商户
```
GET /api/v1/merchant/{id}
Response: { code:0, data: { id, companyName, ... } }
```

---

## 优惠券服务 (coupon-service) — 端口 8089

### 发放优惠券
```
POST /api/v1/coupon/issue
Params: userId, orderId(可选)
Response: { code:0, data: { id, userId, status:"UNUSED", ... } }
```

### 查询用户优惠券
```
GET /api/v1/coupon/user/{userId}
Response: { code:0, data: [{ id, couponId, status, expiredAt }, ...] }
```

### 使用优惠券
```
POST /api/v1/coupon/use
Params: userCouponId
Response: { code:0, data: { id, status:"USED", ... } }
```

---

## 推送服务 (push-service) — 端口 8087

### 发送推送
```
POST /api/v1/push/send
Params: userId, type(sms/app), title, body
Response: { code:0, data: true }
```

---

## WebSocket 网关 — 端口 8086

### 连接地址
```
ws://localhost:8086/ws
```

### 消息格式 (JSON)
```json
// 鉴权请求
{ "type": "AUTH", "merchantId": 1, "token": "mock-token" }

// 鉴权成功响应
{ "type": "AUTH_SUCCESS", "merchantId": 1 }

// 出价请求
{ "type": "BID", "auctionId": "AUCxxx", "merchantId": 1, "bidPrice": "500.00" }

// 出价确认
{ "type": "BID_ACK", "auctionId": "AUCxxx", "merchantId": 1, "bidPrice": "500.00" }

// 竞拍广播 (来自服务端)
{ "type": "AUCTION_NEW_BID", "auctionId": "...", "merchantId": 2, "bidPrice": "550.00" }

// 竞拍结束通知 (来自服务端)
{ "type": "AUCTION_ENDED", "auctionId": "...", "winnerMerchantId": 1, "finalPrice": "600.00" }

// 心跳
发送: { "type": "PING" }
接收: { "type": "PONG", "timestamp": 1234567890 }
```
