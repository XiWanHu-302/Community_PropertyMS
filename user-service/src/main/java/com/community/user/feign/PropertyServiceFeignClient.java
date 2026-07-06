package com.community.user.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

/**
 * property-service 的 Feign 客户端（user-service 调用 property-service）
 */
@FeignClient(name = "property-service")
public interface PropertyServiceFeignClient {

    /** 查询住户未缴费用（物业费+停车费） */
    @GetMapping("/property-fee/unpaid-raw/{householdId}")
    Map<String, Object> getUnpaidFees(@PathVariable Integer householdId);

    /** 入住后生成当月物业费账单 */
    @PostMapping("/property-fee/generate")
    Map<String, Object> generateBill(@RequestBody Map<String, Object> body);
}
