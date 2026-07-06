package com.community.property.feign;

import com.community.common.dto.HouseholdDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * user-service 的 OpenFeign 客户端
 * 使用 raw 端点（不包装 Result），避免泛型擦除导致 LocalDate 等字段丢失
 */
@FeignClient(name = "user-service")
public interface UserServiceFeignClient {

    /** 查询所有在住住户 */
    @GetMapping("/household/active-raw")
    List<HouseholdDTO> getActiveHouseholds();

    /** 查询所有住户（含已搬离，用于查看历史缴费） */
    @GetMapping("/household/all-raw")
    List<HouseholdDTO> getAllHouseholds();

    /** 按住号搜索住户 */
    @GetMapping("/household/search-by-room-raw")
    HouseholdDTO searchByRoom(@RequestParam String buildingNo,
                               @RequestParam Integer floorNo,
                               @RequestParam Integer unitNo);

    /** 按ID查住户简要信息 */
    @GetMapping("/household/{id}/brief-raw")
    HouseholdDTO getBriefById(@PathVariable Integer id);
}
