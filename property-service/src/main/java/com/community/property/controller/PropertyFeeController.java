package com.community.property.controller;

import com.community.common.Result;
import com.community.property.service.FeeDeadlineService;
import com.community.property.service.PropertyFeeService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * 物业费管理 —— 薄层 Controller，业务逻辑委托给 PropertyFeeService
 */
@RestController
@RequestMapping("/property-fee")
public class PropertyFeeController {

    @Resource private PropertyFeeService propertyFeeService;
    @Resource private FeeDeadlineService deadlineService;

    // ==================== 搬离前检查未缴费用 ====================

    @GetMapping("/unpaid-raw/{householdId}")
    public Map<String, Object> unpaidCheckRaw(@PathVariable Integer householdId) {
        return propertyFeeService.unpaidCheckRaw(householdId);
    }

    @GetMapping("/unpaid/{householdId}")
    public Result<Map<String, Object>> unpaidCheck(@PathVariable Integer householdId) {
        return Result.ok(propertyFeeService.unpaidCheck(householdId));
    }

    // ==================== 月报表 ====================

    @GetMapping("/report")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<?> report(@RequestParam(required = false) Integer year,
                            @RequestParam(required = false) Integer month,
                            @RequestParam(required = false) String statusFilter) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();
        return Result.ok(propertyFeeService.report(year, month, statusFilter));
    }

    // ==================== 单户催缴提醒 ====================

    @GetMapping("/reminders/{householdId}")
    public Result<?> remindersForHousehold(@PathVariable Integer householdId) {
        return Result.ok(propertyFeeService.remindersForHousehold(householdId));
    }

    // ==================== 汇总统计 ====================

    @GetMapping("/summary")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(defaultValue = "month") String period,
                                                @RequestParam(required = false) Integer quarter) {
        java.time.LocalDate today = java.time.LocalDate.now();
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();
        if (quarter == null) quarter = (today.getMonthValue() - 1) / 3 + 1;
        return Result.ok(propertyFeeService.summary(year, month, period, quarter));
    }

    // ==================== 查询某户物业费列表 ====================

    @GetMapping("/list/{householdId}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    public Result<?> list(@PathVariable Integer householdId,
                          @RequestParam(required = false) Integer year) {
        return Result.ok(propertyFeeService.listFees(householdId, year));
    }

    // ==================== 缴费 ====================

    @PostMapping("/pay")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    public Result<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        Integer householdId = (Integer) body.get("householdId");
        Integer duration = (Integer) body.getOrDefault("duration", 1);
        if (householdId == null) return Result.fail("请指定住户");
        String handler = (String) body.getOrDefault("handler", "在线缴费");
        return Result.ok(propertyFeeService.pay(householdId, duration, handler));
    }

    // ==================== 入住时生成当月账单 ====================

    @PostMapping("/generate")
    public Result<Void> generateBill(@RequestBody Map<String, Object> body) {
        Integer householdId = (Integer) body.get("householdId");
        if (householdId == null) return Result.fail("缺少 householdId");
        propertyFeeService.generateBill(householdId);
        return Result.ok("已生成当月物业费账单");
    }

    // ==================== 截止日配置 ====================

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
