package com.community.property.controller;

import com.community.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口 —— 验证 property-service 是否正常启动
 */
@RestController
public class HelloController {

    /**
     * 健康检查
     * 启动后访问 http://localhost:8082/hello 验证
     */
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.ok("property-service 启动成功！");
    }
}
