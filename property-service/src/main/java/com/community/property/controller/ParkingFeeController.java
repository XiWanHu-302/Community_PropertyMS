package com.community.property.controller;

import com.community.common.Result;
import com.community.property.service.FeeDeadlineService;
import com.community.property.service.ParkingFeeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 停车费管理 —— 薄层 Controller，业务逻辑委托给 ParkingFeeService
 */
@RestController
@RequestMapping("/parking-fee")
public class ParkingFeeController {

    @Resource private ParkingFeeService parkingFeeService;
    @Resource private FeeDeadlineService deadlineService;

    // ==================== 汇总统计 ====================

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(defaultValue = "month") String period,
                                                @RequestParam(required = false) Integer quarter,
                                                @RequestParam(required = false) String statusFilter) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();
        if (quarter == null) quarter = (today.getMonthValue() - 1) / 3 + 1;
        return Result.ok(parkingFeeService.summary(year, month, period, quarter, statusFilter));
    }

    // ==================== 查询某车位停车费 ====================

    @GetMapping("/list/{spaceNo}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    public Result<?> list(@PathVariable String spaceNo,
                          @RequestParam(required = false) Integer year) {
        return Result.ok(parkingFeeService.listFees(spaceNo, year));
    }

    // ==================== 缴费 ====================

    @PostMapping("/pay")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        String spaceNo = (String) body.get("spaceNo");
        Integer duration = (Integer) body.getOrDefault("duration", 1);
        if (spaceNo == null) return Result.fail("请指定车位");
        String handler = (String) body.getOrDefault("handler", "在线缴费");
        return Result.ok(parkingFeeService.pay(spaceNo, duration, handler));
    }

    // ==================== 截止日配置（与物业费共用 FeeDeadlineService） ====================

    @GetMapping("/deadline")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getDeadline() {
        return Result.ok(deadlineService.getDeadlineInfo());
    }

    @PutMapping("/deadline")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> setDeadline(@RequestBody Map<String, Object> body) {
        int day = (int) body.getOrDefault("deadlineDay", 10);
        if (day < 1 || day > 28) return Result.fail("截止日必须在 1-28 之间");
        deadlineService.setDeadlineDay(day);
        return Result.ok("已更新截止日为每月 " + day + " 号，本月账单状态已同步刷新");
    }
}
