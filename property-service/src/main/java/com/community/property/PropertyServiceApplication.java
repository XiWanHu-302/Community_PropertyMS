package com.community.property;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients   // 启用 OpenFeign，扫描 @FeignClient 接口
@EnableScheduling     // 启用定时任务（每月账单生成 + 逾期标记）
@MapperScan("com.community.property.mapper")
public class PropertyServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PropertyServiceApplication.class, args);
    }
}
