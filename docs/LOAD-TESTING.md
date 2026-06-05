# 压测方案与调优路线

> 这份文档不只是操作手册，每轮压测背后都有一个"为什么"。
> 面试时重要的不是"我压到了 5000 QPS"，而是"我遇到了什么问题、怎么发现的、怎么解决的"。

---

## 总体目标

从初始压测定位瓶颈开始，逐步调优覆盖 接口层 → 缓存层 → 异步化 → JVM → 分库分表，最终达到 **5000+ QPS**。

## 测试架构（最新）

```
商户 HTTP 出价 → Gateway (8080) → auction-service (8085)
                                        → Redis ZSET + Lua（核心出价）
                                        → MySQL auction_record（出价记录落盘）
                                        → Nacos 配置控制写入模式（sync/async）
                                        → RocketMQ 延迟消息（3分钟结束竞拍）
```

关键技术决策影响压测方案：

| 决策 | 对压测的影响 |
|------|-------------|
| 出价统一 HTTP，不走 WS | 压测只需要配 HTTP 请求，不需要维护 WebSocket 连接 |
| 出价路径的 MQ 已去掉 | 少了一层 MQ 延迟，接口响应更快，瓶颈更容易定位 |
| Nacos 配置动态切换写入模式 | 压测时可以对比 sync/async 两种模式下的 TPS |
| endAuction 由 MQ 触发 | endAuction 不需要压，它不属于用户请求路径 |
| 补偿服务独立部署 | 不影响主服务性能，压测时不需要考虑它 |

---

## 压测工具

使用 **Apache JMeter** 进行压测。

```bash
# macOS 安装
brew install jmeter
```

### 核心接口

| 接口 | 类型 | 压测优先级 | 理由 |
|------|------|-----------|------|
| `POST /api/v1/auction/bid` | 写密集 | ⭐ 必须压 | 核心链路：Redis Lua + MySQL 写入，最反映瓶颈 |
| `GET /api/v1/auction/{auctionId}` | 读密集 | ⭐ 建议压 | Redis 读，简单但有参考价值 |
| `POST /api/v1/auction/start` | 写 | 次要 | 工程师手动触发，流量极低 |
| `POST /api/v1/merchant` | 写 | 跳过 | 低频操作 |
| `GET /api/v1/order/{orderId}` | 读 | 有余力再压 | 你已经做过分级缓存，有经验 |

---

## 前置准备

### 1. 准备压测数据

在 setUp Thread Group 中按顺序执行：

```bash
# Step 1: 创建竞拍（只需一次）
curl -X POST http://localhost:8080/api/v1/auction/start \
  -H "Content-Type: application/json" \
  -d '{"orderId": 1, "basePrice": 1000}'
# → 返回 auctionId: "AUC181234567890"

# Step 2: 让商户加入（多个商户）
curl -X POST http://localhost:8080/api/v1/auction/AUC181234567890/join \
  -H "Content-Type: application/json" \
  -d '{"merchantId": 1}'
curl -X POST .../join -d '{"merchantId": 2}'
# ... N 个商户
```

### 2. 挖一个出价深度的坑

因为每商户每场竞拍最多出价 2 次，要用足够多的 merchantId 来支撑压测量。

```
线程数 1000 × 循环 100 = 100,000 次出价
每次需要不同的 merchantId → 至少 50,000 个商户
或者：线程数 500 × 循环 2 = 1000 次出价（每商户最多2次，够用）
```

**推荐方案：** 线程数 500，循环 2，Ramp-Up 10s。每商户只出 1-2 次价，模拟真实场景。

### 3. 修改压测用竞拍时长

```yaml
# auction-service/application.yml
# 修改 AUCTION_TIMEOUT_DELAY_LEVEL（压测时改小）
# 原 delayLevel=7（约3分钟）
# 改为：竞拍时长足够支撑单次压测即可，不需要等它结束
```

**或者更简单：** 不要等竞拍结束再创建下一个。创建多个竞拍，轮流压测。

### 4. 压测前关闭 Nacos 自动刷新（避免干扰）

```
# Nacos 配置中心不参与压测路径，不影响出价性能
# 只在对比 sync/async 模式时才需要关注它
```

### 5. JMeter 配置模板

```
Thread Group
  ├── Number of Threads: 100 / 500 / 1000（递增）
  ├── Ramp-Up: 10s
  └── Loop Count: 2（每商户限2次出价）

  HTTP Request Defaults
  ├── Protocol: http
  ├── Server: localhost
  └── Port: 8080

  HTTP Request: POST /api/v1/auction/bid
  ├── Body: {
  │   "auctionId": "${auctionId}",
  │   "merchantId": ${__Random(1, 10000)},
  │   "orderId": 1,
  │   "bidPrice": ${__Random(1000, 5000)},
  │   "idempotentKey": "${__UUID}"
  │ }

  Listeners
  ├── Summary Report（总览报表）
  ├── Aggregate Report（聚合报表）
  └── Response Time Graph（响应时间图）
```

---

## 第一轮：接口级压测（发现基线瓶颈）

### 目标

定位数据库连接池、Tomcat 线程池、应用代码的基本瓶颈。得到**基线数据**。

### 步骤

```bash
# 1. 启动基础设施
docker compose -f docker-compose-infra.yml up -d

# 2. 编译项目（跳过测试）
mvn clean install -DskipTests

# 3. 启动服务（按顺序）
java -jar registry/target/registry-1.0.0.jar &
sleep 10
java -jar auction-service/target/auction-service-1.0.0.jar &
sleep 10
java -jar gateway/target/gateway-1.0.0.jar &

# 4. 用 setUp Thread Group 创建竞拍 + 加入商户

# 5. 开始压测（100线程，10s Ramp-Up，循环2次）

# 6. 观察指标
jstat -gcutil <auction-service-pid> 1000    # JVM GC 状态
jstack <auction-service-pid> | grep "http"   # 线程数
mysqladmin status -h localhost -u root -proot123  # MySQL 连接
```

### 预期瓶颈

| 瓶颈 | 说明 | 指标 |
|------|------|------|
| Tomcat 线程池 | 默认 200，高并发排队 | 活跃线程 > 200 |
| HikariCP 连接池 | 默认 10 | active > 10 时等待 |
| MySQL 写入 | 每次出价写 `auction_record` | TPS 明显下降 |
| 网关限流 | Redis 令牌桶默认 100/s | 返回 429 Too Many Requests |

### 初始预期结果

| 指标 | 预期值 | 面试可说的观察 |
|------|--------|---------------|
| TPS | ~800 | "第一轮压下去就发现 MySQL 连接池满了，HikariCP 的 10 个连接被 100 个线程抢，大部分线程在等连接" |
| P99 | ~500ms | "响应时间的瓶颈不在业务逻辑，在连接池" |
| 错误率 | < 1% | "抛错的是超过出价次数的商户，这个是预期内的业务限制" |
| CPU | ~70% | "CPU 没跑满，说明瓶颈在 IO（数据库），不在计算" |

### 面试话术

> "第一轮压测用的是默认配置，Tomcat 200 线程、HikariCP 10 连接。100 线程一上去就发现 MySQL 连接数飙高到 10，大部分线程在等连接。这说明系统的第一个瓶颈在数据库连接池，不是业务代码。所以第二轮先把连接池调大，把 Tomcat 线程池也对应调大，先排除这个基础设施瓶颈。"

---

## 第二轮：缓存优化（核心瓶颈突破）

### 目标

引入多级缓存降低 MySQL 压力。这一轮是**整个压测最重要的轮次**，因为系统最大的优化点就在这里。

### 已实现的多级缓存

| 层级 | 技术 | 存什么 | 为什么选这个 |
|------|------|--------|-------------|
| L1 | Caffeine 本地缓存 | 手机型号、估价规则等低频变动数据 | 读多写少，本地缓存延迟最低（微秒级） |
| L2 | Redis ZSET | 出价数据（Lua 原子操作） | ZSET 天然按 score 排序，取最高价 O(1) |
| L3 | Redis HASH | 竞拍状态信息 | 结构化存储，字段级读写 |
| L4 | Redis SET | 参与竞拍的商户列表 | SET 天然去重，适合维护参与人列表 |

### 核心优化：出价数据的 Redis-only 路径

**这是整个系统最巧妙的优化点——出价时 Redis 在校验通过后立即写入，MySQL 只是辅助落盘。**

```
出价请求到达：
  → Lua 脚本在 Redis 内原子完成：
      ① 检查竞拍状态（HASH）
      ② 检查出价次数（STRING 计数器）
      ③ 检查出价金额（ZSET 最大值比较）
      ④ 写入 ZSET + 更新 HASH + 递增计数器
  → 返回 1（成功）
  → MySQL 写入 auction_record（可同步可异步，受 Nacos 配置控制）

关键：决定胜负的核心逻辑（ZSET 最高价）在 Redis 里，不依赖 MySQL。
```

### 面试亮点：Lua 脚本为什么快？

> "Lua 脚本在 Redis 进程内执行所有校验和写入，中间没有网络往返，没有锁竞争。一个脚本搞定：状态检查 + 次数检查 + 金额校验 + 数据写入，4 个操作一次网络开销。这是 Redis 单线程模型的最大优势——原子性不需要应用程序加锁。"

### 压测步骤

```bash
# 调整 HikariCP 连接池
spring.datasource.hikari.maximum-pool-size=50  # 从10调大到50

# 调整 Tomcat 线程池
server.tomcat.threads.max=400  # 从200调大到400
```

### 优化后预期

| 指标 | 优化前 | 优化后 | 变化原因 |
|------|--------|--------|---------|
| TPS | ~800 | ~2000 | Redis 内存操作替代 MySQL 磁盘写入 |
| P99 | ~500ms | ~200ms | 去掉了大部分 MySQL 等待时间 |
| MySQL QPS | 高 | 低 | 出价的读写都走 Redis 了 |

### 面试话术

> "第一轮发现瓶颈在 MySQL 后，我意识到一个事实：**竞拍的核心数据不需要实时落 MySQL。** ZSET 里的出价记录决定了谁赢，而 Redis 的 AOF 持久化足够保证数据不丢。MySQL 只是辅助落盘用于审计和追溯。所以我在第二轮把出价路径改成了 Lua 脚本，TPS 直接从 800 跳到 2000。"

---

## 第三轮：异步化削峰 + Nacos 自适应切换

### 目标

这一轮回答一个面试高频问题：**"MySQL 写入什么时候是非必要延迟？"**

### 已实现的自适应写入

```
Nacos 配置: bid.db.write.mode = sync | async

sync 模式（默认，低流量）:
  Lua Redis 出价 → 同步写 MySQL auction_record
  → TPS: ~2000, 强一致性, MySQL 写多慢等多久

async 模式（高流量切换）:
  Lua Redis 出价 → MQ → 异步消费后写 MySQL auction_record
  → TPS: ~3000, 最终一致性, 出价不等待 MySQL
```

### 为什么需要这个切换

**面试可以说的推理链：**

> "出价记录写入 MySQL 是典型的"非关键写入"——决定谁赢的逻辑已经在 Lua 脚本里完成了，MySQL 只用于追溯和审计。高流量时完全可以用 MQ 异步写，让出价接口不等待 MySQL。Nacos 配置中心的作用就是让运维在流量攀升时一键切换，不需要重启服务。"

### 压测步骤

```bash
# 1. 先测 sync 模式（默认）
# 2. 在 Nacos 控制台修改配置
# 登录 http://localhost:8848/nacos
# Data ID: auction-service-config, Group: RECYCLE_BIDDING
# 添加: bid.db.write.mode=async

# 3. 确认配置已刷新（看 auction-service 日志）
# "配置项已更新: bid.db.write.mode = async (was: sync)"

# 4. 再压一次，对比 TPS
```

### 优化后预期

| 指标 | sync 模式 | async 模式 | 变化 |
|------|-----------|-----------|------|
| 出价 TPS | ~2000 | ~3000 | MySQL 写入不再是瓶颈 |
| 出价 P99 | ~200ms | ~100ms | 去掉了 MySQL insert 耗时 |
| MySQL 写入 | 同步 | MQ 异步削峰 | 平稳 |

### 面试话术

> "第三轮的优化其实是在回答一个问题：**MySQL 写入什么时候可以不要？** 我的答案是：当写入的数据不影响业务决策结果时。出价记录写入 MySQL，就算晚几秒甚至几分钟，也不影响 winner 的判定——因为决定 winner 的 ZSET 已经写进 Redis 了。所以我把 MySQL 写入做成可切换的，sync 模式适用于低流量保证强一致性，async 模式适用于高流量保证核心路径的吞吐。切换由 Nacos 配置中心控制，不用重启服务。"

---

## 第四轮：JVM 调优

### 目标

降低 GC 停顿、优化内存分配。这一轮**前置条件是前两轮已经把系统的 IO 瓶颈基本解决了**，现在 CPU 和内存成为主要瓶颈。

### 推荐 JVM 参数

```bash
java -Xms512m -Xmx1g -Xmn384m \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=100 \
  -XX:ParallelGCThreads=4 \
  -XX:ConcGCThreads=2 \
  -XX:+PrintGCDetails -Xloggc:/tmp/gc.log \
  -jar auction-service/target/auction-service-1.0.0.jar
```

### 参数选择理由

| 参数 | 值 | 为什么是这个值 |
|------|----|---------------|
| `-Xms` | 512m | 初始堆直接设 512m，避免启动后多次扩容 Full GC |
| `-Xmx` | 1g | 12 核 18GB 的 MacBook Pro，给应用的合理上限 |
| `-Xmn` | 384m | 年轻代占堆的约 40%，出价请求对象多数在年轻代就回收了 |
| `UseG1GC` | G1 | 相比 CMS，G1 的停顿时间可预测，适合需要稳定 P99 的接口 |
| `MaxGCPauseMillis` | 100ms | 接口的 P99 目标 100ms，GC 停顿不能超过这个值 |
| `ParallelGCThreads` | 4 | 12 核的机器，给 GC 分配 4 个线程够用，多了抢业务线程 |

### 监控命令

```bash
# GC 实时统计（每秒输出）
jstat -gcutil <pid> 1000

# 查看 GC 停顿分布
grep 'pause' /tmp/gc.log | awk '{print $NF}' | sort -n | tail -10

# 堆转储（Full GC 频繁时抓）
jmap -dump:live,format=b,file=/tmp/heap-$(date +%s).hprof <pid>
```

### 优化后预期

| 指标 | 优化前 | 优化后 |
|------|--------|--------|
| Young GC 频率 | ~5次/秒 | ~1次/秒 |
| Full GC 频率 | ~1次/小时 | ~0次 |
| GC 停顿 P99 | ~50ms | ~10ms |
| TPS | ~3000 | ~4000 |

### 面试话术

> "前三轮解决的是 IO 瓶颈，到第四轮发现 TPS 上不去了，但这次瓶颈在 CPU——GC 线程在抢业务线程的时间。G1GC 的 `MaxGCPauseMillis` 设成 100ms 是关键参数——因为我观察到出价接口的 P99 响应时间大约在 100-200ms，GC 停顿必须小于这个值才不会成为瓶颈。调完之后 Young GC 从每秒 5 次降到 1 次，TPS 又涨了一截。"

---

## 第五轮：分库分表方案

### 问题分析

持续压测 10 分钟以上，`auction_record` 表数据量增长到百万级，**写入性能下降约 30%**。

### 为什么出价数据在 Redis 里了还是会有 MySQL 问题？

出价的确经过了 Lua → Redis ZSET，但 MySQL 还有两个写入点：
1. `AuctionRecord` 出价记录落盘（`source="lua"`）
2. `endAuction()` 时从 ZSET 批量读取后再批量写入（`source="redis"`）

当压测持续运行时，`auction_record` 表的数据量持续增长，B+ 树索引层级加深，写入逐渐变慢。

### 分表方案

按 `order_id` 分片（用户维度），共 **16 张表**（0-15）。

```yaml
# ShardingSphere-JDBC 配置
spring:
  shardingsphere:
    datasource:
      names: ds0
      ds0:
        url: jdbc:mysql://localhost:3306/recycle_bidding
        username: root
        password: root123
    sharding:
      tables:
        auction_record:
          actual-data-nodes: ds0.auction_record_$->{0..15}
          table-strategy:
            inline:
              sharding-column: order_id
              algorithm-expression: auction_record_$->{order_id % 16}
```

### 改造要点

1. 引入 `shardingsphere-jdbc-core-spring-boot-starter`
2. MyBatis-Plus 的数据源由 ShardingSphere 接管
3. 查询时自动路由：分片键 = `order_id`
4. 跨分片查询（如商户全部历史出价）通过 Elasticsearch 汇总

### 优化后预期

| 指标 | 单表 | 16 分表 |
|------|------|---------|
| 写入 TPS | ~4000 | 5000+ |
| P99 | ~80ms | ~50ms |
| 单表数据量 | 百万级/天 | 均匀分布到 16 张表 |

### 面试话术

> "第五轮的优化是在第三轮的基础上做的。异步化之后，TPS 到了 3000-4000，但长时间运行还是会因为 `auction_record` 表太大而性能下降。分表是按 `order_id` 分 16 片——这个分片键选择基于一个事实：**所有竞拍查询都是按 order_id 来的，按 order_id 分片不会产生跨片查询。**"

---

## 预期结果总结

| 轮次 | 操作 | TPS | P99 | 瓶颈是什么 | 怎么发现的 |
|------|------|-----|-----|-----------|-----------|
| 初始 | 默认配置 | ~800 | ~500ms | HikariCP 连接池 10 | `jstat` 看到大量线程等待连接 |
| 第二轮 | +Redis Lua 缓存 | ~2000 | ~200ms | MySQL 写入 | 发现出价的胜负在 Redis 已决定，MySQL 只是辅助 |
| 第三轮 | +Nacos 异步切换 | ~3000 | ~100ms | CPU（GC） | async 模式下去掉了 MySQL，瓶颈变成 GC |
| 第四轮 | +JVM 调优 G1GC | ~4000 | ~80ms | 表数据量大 | 长压测后发现 `auction_record` 写入变慢 |
| 第五轮 | +16 分表 | **5000+** | **~50ms** | 暂未发现 | 分表后写入均匀分布 |

---

## 面试叙事线：面试时能讲的完整故事

如果你面试时只能讲 3 分钟，按这个顺序说：

### 第一段（30秒）：背景与迷茫

> "这个项目是二手回收竞拍系统，商户通过盲拍出价。核心写接口是出价接口——每秒成百上千次的 Redis 写入和 MySQL 写入。压测初期我很迷茫：这是个写密集系统，写请求怎么压？每个商户最多出价 2 次，测试数据怎么构造？"

### 第二段（60秒）：发现问题与解决

> "第一轮盲压，发现瓶颈在 MySQL 连接池——HikariCP 默认 10 个连接，100 个线程抢，大部分在等。然后我重新审视了业务流程：**决定竞拍胜负的核心逻辑在 Lua 脚本里就完成了，MySQL 写入只是辅助落盘。** 于是第二轮把出价路径全部走 Redis，TPS 从 800 跳到 2000。"

### 第三段（60秒）：渐进优化

> "第三轮加了 Nacos 配置中心控制写入模式——低流量时同步写 MySQL 保证强一致性，高流量时异步 MQ 写 MySQL 保证核心路径吞吐。第四轮发现 GC 成了新瓶颈，调了 G1GC 参数又涨了 1000 TPS。第五轮长时间压测发现 `auction_record` 表太大导致写入下降，按 `order_id` 分 16 片，最终到 5000+。"

### 第四段（30秒）：总结

> "整个过程的核���思路不是把参数调大，而是**不断追问每一层写入是不是必要的**。不必要的去掉，必要但不能快的异步化，异步化之后还有瓶颈就做水平扩展。面试官如果问我最大的收获，就是这个不断追问题的方法论。"

---

## 附录：JMeter 配置细节

### 竞拍出价场景

```
Thread Group
  Number of Threads: 100 (逐步增加到 500/1000)
  Ramp-Up: 10s
  Loop Count: 2 (每人最多出价 2 次)

HTTP Request Defaults
  Server: localhost
  Port: 8080

HTTP Request: POST /api/v1/auction/bid
  Body (JSON):
  {
    "auctionId": "${__P(auctionId)}",
    "merchantId": ${__Random(1, 100000)},
    "orderId": 1,
    "bidPrice": ${__Random(1000, 5000)},
    "idempotentKey": "jmeter-${__UUID}"
  }

Listeners
  - Summary Report
  - Aggregate Report
  - Response Time Graph
```

### setUp Thread Group（前置准备）

```
setUp Thread Group
  Number of Threads: 1
  Loop Count: 1

HTTP Request: POST /api/v1/auction/start
  Body: {"orderId": 1, "basePrice": 1000}
  → 提取 auctionId 存为 __setProperty(auctionId, ...)

HTTP Request: POST /api/v1/auction/{auctionId}/join
  Body: {"merchantId": 1}  (重复 N 次，覆盖商户池)
```

### tearDown Thread Group（清理，可选）

```
tearDown Thread Group
  Number of Threads: 1

HTTP Request: GET /api/v1/auction/{auctionId}
  → 验证结果
```

### 启动方式

```bash
# 命令行启动（非 GUI，适合压测）
jmeter -n -t bidding-test.jmx -l result.jtl -e -o report/

# GUI 启动（调试用）
jmeter -t bidding-test.jmx
```
