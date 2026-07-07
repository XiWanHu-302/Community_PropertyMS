package com.community.property.feign;

import com.community.common.dto.HouseholdDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.openfeign.FallbackFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * UserServiceFeignClient 的服务降级工厂
 * <p>
 * 使用 FallbackFactory（而非简单的 fallback）是为了捕获 LoadBalancer
 * 在发起调用前就抛出的 ServiceUnavailable 等异常。
 * <p>
 * 降级策略：
 * <ul>
 *   <li>住户列表 → 空列表（调用方已有 null 检查保护）</li>
 *   <li>单个住户查询 → null（调用方会返回 Result.fail）</li>
 * </ul>
 */
@Component
public class UserServiceFeignClientFallback implements FallbackFactory<UserServiceFeignClient> {

    private static final Logger log = LoggerFactory.getLogger(UserServiceFeignClientFallback.class);

    @Override
    public UserServiceFeignClient create(Throwable cause) {
        log.warn("user-service 调用失败，触发服务降级。原因：{}", cause.getMessage());

        return new UserServiceFeignClient() {
            @Override
            public List<HouseholdDTO> getActiveHouseholds() {
                return Collections.emptyList();
            }

            @Override
            public List<HouseholdDTO> getAllHouseholds() {
                return Collections.emptyList();
            }

            @Override
            public HouseholdDTO searchByRoom(String buildingNo, Integer floorNo, Integer unitNo) {
                return null;
            }

            @Override
            public HouseholdDTO getBriefById(Integer id) {
                return null;
            }
        };
    }
}
