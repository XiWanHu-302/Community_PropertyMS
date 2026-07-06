package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.user.entity.Household;
import com.community.user.mapper.BuildingMapper;
import com.community.user.mapper.HouseholdMapper;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 首页统计控制器
 */
@RestController
@RequestMapping("/dashboard")
public class DashboardController {

    @Resource
    private HouseholdMapper householdMapper;

    @Resource
    private BuildingMapper buildingMapper;

    /**
     * 管理员首页统计数据
     * GET /dashboard/stats
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("householdCount", householdMapper.selectCount(
                new LambdaQueryWrapper<Household>().eq(Household::getStatus, 1)));
        data.put("buildingCount", buildingMapper.selectCount(null));
        // 停车位和维修数后续再接入
        data.put("parkingCount", 0);
        data.put("pendingRepair", 0);
        return Result.ok(data);
    }
}
