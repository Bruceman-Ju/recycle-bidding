# 回收竞拍系统 (Recycle Bidding System)

## 项目概述

一个完整的二手回收交易平台系统，覆盖 **用户下单 → 工程师验机 → 商户竞拍 → 支付打款 → 优惠券发放** 的完整业务闭环。

核心业务流程：
1. **用户下单** → 提交回收订单，系统给出初始估价
2. **工程师验机** → 工程师接单、检测设备、提交二次估价
3. **商户竞拍** → 多家商户在限定时间内竞拍出价，Redis + Lua 保证原子性
4. **成交支付** → 用户确认后，竞拍胜出商户完成支付
5. **优惠券发放** → 支付成功后自动发放优惠券

## 技术栈

| 类别 | 技术 | 版本 | 用途           |
|------|------|------|--------------|
| 语言 | Java | 17 | 主开发语言        |
| 框架 | Spring Boot | 3.2.5 | 微服务基础框架      |
| 网关 | Spring Cloud Gateway | 2023.0.3 | API 路由、限流、鉴权 |
| 注册中心 | Netflix Eureka | 2023.0.3 | 服务注册与发现      |
| ORM | MyBatis-Plus | 3.5.7 | 数据持久层        |
| 数据库 | MySQL | 8.0+ | 业务数据持久化      |
| 缓存 | Redis | 7.x | 竞拍数据、令牌桶限流   |
| 消息队列 | RocketMQ | 4.9+ | 异步解耦、削峰填谷    |
| WebSocket | Netty | 4.1.111 | 通知待竞拍商品    |
| 熔断 | Resilience4j | 2.2.0 | 服务熔断与降级      |
| 构建 | Maven | 3.9+ | 多模块构建        |
| 容器化 | Docker Compose | - | 一键部署         |

## 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      Gateway (8080)                         │
│          Spring Cloud Gateway + 限流 + 鉴权 + 熔断            │
└──┬────┬────┬────┬────┬────┬────┬────┬────┬────┬────┬──────┘
   │    │    │    │    │    │    │    │    │    │    │
┌──▼┐ ┌▼──┐┌▼──┐┌▼──┐┌▼──┐┌▼──┐┌▼──┐┌▼──┐┌▼──┐┌▼──┐┌▼───┐
│Reg│ │Ord│ │Eng│ │Mer│ │Auc│ │ WS│ │Psh│ │Pay│ │Cpn│ │Web │
│   │ │   │ │   │ │   │ │   │ │   │ │   │ │   │ │   │ │Sck │
│8761│ │8082│ │8083│ │8084│ │8085│ │8086│ │8087│ │8088│ │8089│ │
└───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘ └────┘
                         │
         ┌───────────────┼───────────────┐
         ▼               ▼               ▼
      MySQL(3306)     Redis(6379)    RocketMQ(9876)
```

### 核心模块职责

| 模块 | 端口 | 说明 |
|------|------|------|
| common | - | 共享模块：DTO、常量、枚举、异常、工具类 |
| registry | 8761 | Eureka 注册中心 |
| gateway | 8080 | Spring Cloud Gateway + 令牌桶限流 + 鉴权 + 熔断 |
| order-service | 8082 | 订单服务：10 状态状态机 + 事件溯源 |
| engineer-service | 8083 | 工程师服务：接单/验机/估价 |
| merchant-service | 8084 | 商户服务：信息管理 |
| auction-service | 8085 | 竞拍核心：Redis ZSET + Lua 原子出价 |
| websocket-gateway | 8086 | Netty WebSocket 网关：MQ 解耦 |
| push-service | 8087 | 推送服务，已经替换成 websocket 推送 |
| payment-service | 8088 | 支付服务：Mock + 事务消息 |
| coupon-service | 8089 | 优惠券服务：发放/使用/过期管理 |

## 快速启动

### 环境要求
- JDK 17+
- Maven 3.9+
- Docker & Docker Compose

### 第一步：启动基础设施
```bash
cd recycle-bidding
docker compose -f docker-compose-infra.yml up -d
```

### 第二步：编译项目
```bash
mvn clean install -DskipTests
```

### 第三步：启动服务（按顺序）
```bash
# 终端1: 注册中心
java -jar registry/target/registry-1.0.0.jar

# 终端2: 订单服务
java -jar order-service/target/order-service-1.0.0.jar

# 终端3: 工程师服务
java -jar engineer-service/target/engineer-service-1.0.0.jar

# 终端4: 商户服务
java -jar merchant-service/target/merchant-service-1.0.0.jar

# 终端5: 竞拍服务
java -jar auction-service/target/auction-service-1.0.0.jar

# 终端6: WebSocket 网关
java -jar websocket-gateway/target/websocket-gateway-1.0.0.jar

# 终端7: 推送服务
java -jar push-service/target/push-service-1.0.0.jar

# 终端8: 支付服务
java -jar payment-service/target/payment-service-1.0.0.jar

# 终端9: 优惠券服务
java -jar coupon-service/target/coupon-service-1.0.0.jar

# 终端10: API 网关
java -jar gateway/target/gateway-1.0.0.jar
```

### 第四步：验证
```bash
# 检查 Eureka 注册中心
curl http://localhost:8761

# 创建订单
curl -X POST http://localhost:8080/api/v1/order/create \
  -H "Authorization: Bearer mock-token-1" \
  -d "userId=1&phoneModelId=1&initialEstimate=1000.00"
```

### Docker Compose 一键部署
```bash
docker compose -f docker-compose-infra.yml up -d
docker compose -f docker-compose-app.yml up -d
```

## 技术选型理由

| 决策 | 选型 | 理由 |
|------|------|------|
| 微服务框架 | Spring Boot 3 + Cloud 2023 | 生态成熟，团队熟悉度高 |
| 网关 | Spring Cloud Gateway | 响应式（WebFlux），非阻塞，适合高并发 |
| 注册中心 | Eureka | 轻量级，与 Spring Cloud 原生集成 |
| 消息队列 | RocketMQ | 支持延迟消息（竞拍超时）、事务消息（支付最终一致性） |
| WebSocket | Netty | 高性能 NIO，相比 Tomcat WebSocket 吞吐量更高 |
| 缓存 | Redis + Caffeine | 多级缓存：本地缓存静态数据，Redis 缓存竞拍动态数据 |
| 原子操作 | Lua 脚本 | 保证竞拍出价在 Redis 中的原子性 |

## 高并发设计要点

### 1. Lua 原子出价（竞拍公平性保证）
- 竞拍出价在 Redis 中以 ZSET 存储（score=出价金额，member=merchantId:timestamp）
- Lua 脚本一次完成：状态检查 + 金额校验 + ZSET 写入
- 三点校验：竞拍是否运行中、是否高于当前最高价、首次出价是否 >= 底价

### 2. Netty WebSocket 推送待竞拍商品

- 前端页面开放注册入口，进入后和后端简历 websocket 链接。
- 工程师发起竞拍，推送待竞拍商品给所有注册商户。

### 3. 三级超时保障（竞拍 3 分钟超时）
- **第一级**：RocketMQ 延迟消息（delayLevel=7，约 3 分钟）
- **第二级**：暂定内存 ConcurrentHashMap 兜底扫描（@Scheduled 每秒检查）
- **第三级**：Redis TTL 自动过期（Redis Key 过期作为最后防线）

### 4. 状态机驱动（订单 10 个状态）
- `OrderStateMachine` 定义完整状态流转映射表
- 每个状态变更都记录 `OrderEventLog`（事件溯源）
- 每次状态变更发送 MQ 消息（其他服务订阅消费）

### 5. 多级缓存体系
- **Caffeine 本地缓存**：手机型号、估价规则等静态数据
- **Redis ZSET**：竞拍出价数据（时效性 > 持久性）
- **Redis HASH**：竞拍状态信息
- **MySQL 最终落盘**：竞拍结束后 Redis 数据批量落库

## 项目结构

```
recycle-bidding/
├── pom.xml                          # Maven 父 POM
├── common/                          # 共享模块
│   └── src/main/java/.../
│       ├── constant/                 # 常量（SystemConstants, AuctionConstants, OrderStatus）
│       ├── exception/               # 异常（BizException, ErrorCode）
│       ├── result/                  # 统一响应（Result<T>, PageResult<T>）
│       └── util/                    # 工具类（TraceIdUtil, JsonUtil）
├── registry/                        # Eureka 注册中心
├── gateway/                         # API 网关
│   └── src/main/java/.../
│       ├── config/                  # 路由配置 + 限流配置
│       ├── filter/                  # TraceIdFilter + AuthGlobalFilter
│       └── handler/                 # FallbackController
├── order-service/                   # 订单服务
│   └── src/main/java/.../order/
│       ├── entity/                  # Order, OrderEventLog
│       ├── repository/              # MyBatis-Plus Mapper
│       ├── service/                 # OrderService, OrderStateMachine
│       ├── controller/              # REST API
│       └── listener/                # MQ 消费者
├── engineer-service/                # 工程师服务
├── merchant-service/                # 商户服务
├── auction-service/                 # 竞拍服务
│   └── src/main/java/.../auction/
│       ├── entity/
│       ├── repository/              # AuctionRedisRepository (Redis)
│       ├── service/                 # AuctionBidService (Lua), AuctionTimerService
│       ├── controller/
│       ├── config/                  # RedisLuaConfig
│       ├── listener/
│       └── resources/scripts/       # bid.lua
├── websocket-gateway/               # WebSocket 网关
│   └── src/main/java/.../ws/
│       ├── server/                  # WebSocketServer (Netty), ChannelInitializer
│       ├── handler/                 # AuthHandler, BidRequestHandler, HeartbeatHandler
│       ├── session/                 # SessionManager, AuctionSessionManager
│       ├── mq/                      # RocketMQProducer, RocketMQConsumer
│       └── pubsub/                  # RedisPubSubListener
├── push-service/                    # 推送服务，已经替换为 websocket 推送
├── payment-service/                 # 支付服务
├── coupon-service/                  # 优惠券服务
├── docs/                            # 文档
│   ├── API.md                       # API 接口文档
│   ├── LOAD-TESTING.md              # 压测方案与调优路线
│   └── recycle-bidding-postman.json # Postman 集合
├── sql/                             # 初始化 SQL
├── rocketmq/                        # RocketMQ 配置
├── docker-compose-infra.yml         # 基础设施容器化
├── docker-compose-app.yml           # 微服务容器化
└── README.md                        # 本文件
```