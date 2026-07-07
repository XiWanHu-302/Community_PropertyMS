package com.community.user.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * PropertyServiceFeignClient 的服务降级工厂
 * <p>
 * 使用 FallbackFactory（而非简单的 fallback）是为了捕获 LoadBalancer
 * 在发起调用前就抛出的 ServiceUnavailable 等异常。
 * <p>
 * 降级策略：
 * <ul>
 *   <li>getUnpaidFees → canMoveOut=true（不阻塞搬离操作）</li>
 *   <li>generateBill → success=false（调用方已有 try-catch 兜底）</li>
 * </ul>
 */
@Component
public class PropertyServiceFeignClientFallback implements FallbackFactory<PropertyServiceFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(PropertyServiceFeignClientFallback.class);

    @Override
    public PropertyServiceFeignClient create(Throwable cause) {
        log.warn("property-service 调用失败，触发服务降级。原因：{}", cause.getMessage());

        return new PropertyServiceFeignClient() {
            @Override
            public Map<String, Object> getUnpaidFees(Integer householdId) {
                Map<String, Object> result = new HashMap<>();
                result.put("householdId", householdId);
                result.put("propTotal", BigDecimal.ZERO);
                result.put("parkTotal", BigDecimal.ZERO);
                result.put("totalUnpaid", BigDecimal.ZERO);
                result.put("canMoveOut", true);
                result.put("details", Collections.emptyList());
                return result;
            }

            @Override
            public Map<String, Object> generateBill(Map<String, Object> body) {
                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "物业费服务暂不可用，请稍后重试");
                return result;
            }
        };
    }
}
