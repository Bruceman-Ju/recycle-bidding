package com.recycle.bidding.engineer;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication(scanBasePackages = {"com.recycle.bidding"})
@EnableDiscoveryClient
@MapperScan("com.recycle.bidding.engineer.repository")
public class EngineerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngineerApplication.class, args);
    }
}
