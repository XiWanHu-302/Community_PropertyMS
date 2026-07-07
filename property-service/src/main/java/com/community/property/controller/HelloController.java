package com.community.property.controller;

import com.community.common.Result;
import com.community.common.dto.HouseholdDTO;
import com.community.property.feign.UserServiceFeignClient;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 测试接口 —— 验证 property-service 是否正常启动
 */
@RestController
public class HelloController {

    @Resource
    private UserServiceFeignClient userFeignClient;

    /**
     * 健康检查
     * 启动后访问 http://localhost:8082/hello 验证
     */
    @GetMapping("/hello")
    public Result<String> hello() {
        return Result.ok("property-service 启动成功！");
    }

    /**
     * Feign 降级测试 —— 验证停掉 user-service 后 Fallback 是否生效
     * <p>
     * 无需 token（已 permitAll），直接 GET 即可
     * <p>
     * 有 Fallback：返回 200 + "降级生效 — 返回 null（住户不存在）"
     * 无 Fallback：返回 500 + FeignException 堆栈
     */
    @GetMapping("/feign-test")
    public Result<String> feignTest() {
        try {
            HouseholdDTO h = userFeignClient.getBriefById(1);
            if (h == null) {
                return Result.ok("降级生效 — Feign 调用 user-service 失败，Fallback 返回 null");
            }
            return Result.ok("Feign 调用成功，住户：" + h.getOwnerName() + "（user-service 正常运行中）");
        } catch (Exception e) {
            return Result.fail("Feign 调用异常（Fallback 未生效）: " + e.getClass().getSimpleName() + " — " + e.getMessage());
        }
    }
}
