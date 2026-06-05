package com.recycle.bidding.auction.config;

import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Nacos 配置中心管理器
 *
 * 加载 auction-service 的动态配置，支持运行时热更新。
 * 当运维人员在 Nacos 控制台修改配置后，监听器实时推送新值。
 *
 * 生产级特性：
 * - 使用 Nacos 长轮询机制（非定时轮询），配置变更毫秒级生效
 * - 内置本地缓存兜底：Nacos 宕机时使用上一次拉取的值
 * - 监听器自动反注册，避免重启时泄漏
 */
@Slf4j
@Component
public class NacosConfigManager {

    /** 当前出价记录的写入模式：sync = 同步写 MySQL，async = 异步 MQ 写 MySQL */
    private volatile String bidDbWriteMode = "sync";

    private ConfigService configService;
    private Executor listenerExecutor;

    @Value("${nacos.config.server-addr:localhost:8848}")
    private String serverAddr;

    @Value("${nacos.config.data-id:auction-service-config}")
    private String dataId;

    @Value("${nacos.config.group:RECYCLE_BIDDING}")
    private String group;

    @PostConstruct
    public void init() {
        try {
            listenerExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "nacos-config-listener");
                t.setDaemon(true);
                return t;
            });

            configService = NacosFactory.createConfigService(serverAddr);
            // 1. 首次拉取配置（同步，带 3 秒超时）
            String config = configService.getConfig(dataId, group, 3000);
            if (config != null) {
                parseAndApply(config);
                log.info("Nacos 配置加载成功: dataId={}, group={}, config={}", dataId, group, config);
            } else {
                log.warn("Nacos 配置为空，使用默认值 bidDbWriteMode=sync");
            }

            // 2. 注册监听器（长轮询，配置变更自动回调）
            configService.addListener(dataId, group, new Listener() {
                @Override
                public Executor getExecutor() {
                    return listenerExecutor;
                }

                @Override
                public void receiveConfigInfo(String configInfo) {
                    log.info("Nacos 配置已变更: configInfo={}", configInfo);
                    parseAndApply(configInfo);
                }
            });

        } catch (Exception e) {
            log.error("Nacos 配置中心初始化失败，使用默认配置 bidDbWriteMode=sync", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (configService != null) {
            try {
                configService.removeListener(dataId, group, null);
            } catch (Exception e) {
                log.warn("移除Nacos监听器失败", e);
            }
        }
    }

    /**
     * 解析并应用 Nacos 配置内容
     * 配置格式：properties 格式，如 "bid.db.write.mode=async"
     */
    private void parseAndApply(String config) {
        if (config == null || config.isBlank()) return;

        for (String line : config.split("\n")) {
            line = line.trim();
            if (line.isBlank() || line.startsWith("#")) continue;

            int eqIdx = line.indexOf('=');
            if (eqIdx < 0) continue;

            String key = line.substring(0, eqIdx).trim();
            String value = line.substring(eqIdx + 1).trim();

            if ("bid.db.write.mode".equals(key)) {
                String oldMode = this.bidDbWriteMode;
                this.bidDbWriteMode = value;
                log.info("配置项已更新: bid.db.write.mode = {} (was: {})", value, oldMode);
            }
        }
    }

    /**
     * 获取当前出价记录写入模式
     */
    public String getBidDbWriteMode() {
        return bidDbWriteMode;
    }

    /**
     * 当前是否为异步写入模式
     */
    public boolean isAsyncMode() {
        return "async".equalsIgnoreCase(bidDbWriteMode);
    }
}
