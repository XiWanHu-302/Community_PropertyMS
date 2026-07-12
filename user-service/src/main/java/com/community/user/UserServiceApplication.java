package com.community.user;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

/**
 * 用户服务启动类
 * 负责：用户认证、楼栋管理、住户管理、维修管理
 *
 * @EnableDiscoveryClient 将服务注册到 Nacos，让其他服务能发现它
 * @EnableFeignClients    启用 OpenFeign，调用 property-service
 * @MapperScan            扫描 MyBatis Mapper 接口
 */
@SpringBootApplication
@ComponentScan("com.community")   // 扫描 common 模块的 Bean（JwtUtil 等）
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.community.user.mapper")
public class UserServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }
}
