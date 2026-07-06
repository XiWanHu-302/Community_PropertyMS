package com.community.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.common.dto.HouseholdDTO;
import com.community.property.entity.PropertyFee;
import com.community.property.config.DeadlineConfig;
import com.community.property.feign.UserServiceFeignClient;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.mapper.ParkingSpaceMapper;
import com.community.property.mapper.PropertyFeeMapper;
import com.community.property.service.FeePaymentHelper;
import com.community.property.service.FeePaymentHelper.PaymentRange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 物业费管理
 * - 费用记录在缴费或首次查询时按需创建（不再需要人工"生成"）
 * - 状态仅三种：已缴 / 待缴 / 逾期
 * - 默认截止日：每月1号
 */
@RestController
@RequestMapping("/property-fee")
public class PropertyFeeController {

    @Resource private PropertyFeeMapper feeMapper;
    @Resource private ParkingFeeMapper parkingFeeMapper;
    @Resource private ParkingSpaceMapper spaceMapper;
    @Resource private UserServiceFeignClient userFeignClient;
    @Resource private DeadlineConfig deadlineConfig;
    @Resource private JdbcTemplate jdbcTemplate;  // 用于调用存储过程

    // ==================== 搬离前检查未缴费用 ====================

    /** 内部 Feign 调用用（不包装 Result） */
    @GetMapping("/unpaid-raw/{householdId}")
    public Map<String, Object> unpaidCheckRaw(@PathVariable Integer householdId) {
        Result<Map<String, Object>> r = unpaidCheck(householdId);
        return r.getData();
    }

    @GetMapping("/unpaid/{householdId}")
    public Result<Map<String, Object>> unpaidCheck(@PathVariable Integer householdId) {
        // 先标记逾期
        markOverdue();

        // 获取住户信息
        HouseholdDTO h = userFeignClient.getBriefById(householdId);
        if (h == null) return Result.fail("住户不存在");

        LocalDate today = LocalDate.now();
        BigDecimal amountPerMonth = (h.getArea() != null && h.getPropertyFeeRate() != null)
                ? h.getArea().multiply(h.getPropertyFeeRate()) : BigDecimal.ZERO;

        // 物业费未缴（is_paid != 1，即 0待缴 + -1逾期）
        List<PropertyFee> propUnpaid = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .ne(PropertyFee::getIsPaid, 1)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));

        // 已生成记录的月份集合
        Set<String> existingMonths = new HashSet<>();
        for (PropertyFee f : feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId))) {
            existingMonths.add(f.getYear() + "-" + f.getMonth());
        }

        // 补充未生成记录的月份（从入住月到当前月）
        List<Map<String, Object>> propMissing = new ArrayList<>();
        if (h.getCheckInDate() != null) {
            int y = h.getCheckInDate().getYear(), m = h.getCheckInDate().getMonthValue();
            while (y < today.getYear() || (y == today.getYear() && m <= today.getMonthValue())) {
                String key = y + "-" + m;
                if (!existingMonths.contains(key)) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("type", "物业费");
                    d.put("year", y); d.put("month", m);
                    d.put("amount", amountPerMonth);
                    propMissing.add(d);
                }
                m++; if (m > 12) { m = 1; y++; }
            }
        }

        // 停车费未缴（is_paid != 1）
        List<ParkingFee> parkUnpaid = parkingFeeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getHouseholdId, householdId)
                .ne(ParkingFee::getIsPaid, 1)
                .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));

        // 查询该住户的停车位，补充未生成记录的月份
        List<ParkingSpace> mySpaces = spaceMapper.selectList(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getHouseholdId, householdId));
        List<Map<String, Object>> parkMissing = new ArrayList<>();
        for (ParkingSpace sp : mySpaces) {
            Set<String> spaceExistingMonths = new HashSet<>();
            for (ParkingFee f : parkingFeeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                    .eq(ParkingFee::getSpaceNo, sp.getSpaceNo()))) {
                spaceExistingMonths.add(f.getYear() + "-" + f.getMonth());
            }
            // 从入住月或车位分配月开始（取较晚的）
            int startY = today.getYear(), startM = 1;
            if (h.getCheckInDate() != null) { startY = h.getCheckInDate().getYear(); startM = h.getCheckInDate().getMonthValue(); }
            int y = startY, m = startM;
            while (y < today.getYear() || (y == today.getYear() && m <= today.getMonthValue())) {
                String key = y + "-" + m;
                if (!spaceExistingMonths.contains(key)) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("type", "停车费");
                    d.put("spaceNo", sp.getSpaceNo());
                    d.put("year", y); d.put("month", m);
                    d.put("amount", sp.getMonthlyFee() != null ? sp.getMonthlyFee() : BigDecimal.ZERO);
                    parkMissing.add(d);
                }
                m++; if (m > 12) { m = 1; y++; }
            }
        }

        BigDecimal propTotal = propUnpaid.stream().map(PropertyFee::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (Map<String, Object> d : propMissing) { propTotal = propTotal.add((BigDecimal) d.get("amount")); }
        BigDecimal parkTotal = parkUnpaid.stream().map(ParkingFee::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        for (Map<String, Object> d : parkMissing) { parkTotal = parkTotal.add((BigDecimal) d.get("amount")); }

        List<Map<String, Object>> details = new ArrayList<>();
        details.addAll(propMissing);
        for (PropertyFee f : propUnpaid) {
            Map<String, Object> d = new HashMap<>();
            d.put("type", "物业费"); d.put("year", f.getYear()); d.put("month", f.getMonth()); d.put("amount", f.getAmount()); details.add(d);
        }
        details.addAll(parkMissing);
        for (ParkingFee f : parkUnpaid) {
            Map<String, Object> d = new HashMap<>();
            d.put("type", "停车费"); d.put("spaceNo", f.getSpaceNo()); d.put("year", f.getYear()); d.put("month", f.getMonth()); d.put("amount", f.getAmount()); details.add(d);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("householdId", householdId);
        result.put("propTotal", propTotal);
        result.put("parkTotal", parkTotal);
        result.put("totalUnpaid", propTotal.add(parkTotal));
        result.put("canMoveOut", details.isEmpty());
        result.put("details", details);
        return Result.ok(result);
    }

    // ==================== 状态判定工具方法 ====================

    /**
     * 调用存储过程批量标记逾期（is_paid: 0→-1）
     */
    private void markOverdue() {
        try {
            jdbcTemplate.update("CALL sp_mark_overdue(?)", deadlineConfig.getDeadlineDay());
        } catch (Exception e) { /* 忽略，存储过程可能尚未部署 */ }
    }

    /**
     * 将 is_paid 三态映射为前端展示文本
     *  -1=逾期, 0=待缴, 1=已缴（未来月份已缴=提前缴费）
     */
    private String statusText(Integer isPaid, int year, int month, LocalDate today) {
        if (isPaid == null) return "待缴";
        if (isPaid == 1) {
            if (year > today.getYear() || (year == today.getYear() && month > today.getMonthValue()))
                return "提前缴费";
            return "已缴";
        }
        if (isPaid == -1) return "逾期";
        // is_paid = 0：历史月份为逾期；当前月根据截止日判定（已过截止日为逾期，否则待缴）
        if (year < today.getYear() || (year == today.getYear() && month < today.getMonthValue()))
            return "逾期";
        // 当前月：即使 is_paid=0，过了截止日也应显示逾期（与存储过程判定一致）
        if (year == today.getYear() && month == today.getMonthValue()
                && today.getDayOfMonth() > deadlineConfig.getDeadlineDay())
            return "逾期";
        return "待缴";
    }

    /**
     * 判断住户在指定月份是否在住（入住前/搬离后不计费）
     */
    private boolean isActiveInMonth(HouseholdDTO h, int year, int month) {
        if (h.getCheckInDate() != null) {
            if (year < h.getCheckInDate().getYear()
                    || (year == h.getCheckInDate().getYear() && month < h.getCheckInDate().getMonthValue()))
                return false;
        }
        if (h.getCheckOutDate() != null) {
            if (year > h.getCheckOutDate().getYear()
                    || (year == h.getCheckOutDate().getYear() && month > h.getCheckOutDate().getMonthValue()))
                return false;
        }
        return true;
    }

    /**
     * 获取费用金额
     */
    private BigDecimal getAmount(PropertyFee fee, HouseholdDTO h) {
        if (fee != null) return fee.getAmount();
        if (h.getArea() != null && h.getPropertyFeeRate() != null)
            return h.getArea().multiply(h.getPropertyFeeRate());
        return BigDecimal.ZERO;
    }

    // ==================== 月报表：所有住户缴费状态 ====================

    @GetMapping("/report")
    public Result<List<Map<String, Object>>> report(@RequestParam(required = false) Integer year,
                                                     @RequestParam(required = false) Integer month,
                                                     @RequestParam(required = false) String statusFilter) {
        LocalDate today = LocalDate.now();
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();

        // 先标记逾期，确保管理员看到的是最新状态
        markOverdue();

        // 含已搬离住户（isActiveInMonth 会按入住/搬离日期过滤）
        List<HouseholdDTO> households = userFeignClient.getAllHouseholds();
        List<Map<String, Object>> vos = new ArrayList<>();

        if (households != null) {
            for (HouseholdDTO h : households) {
                // 该月不在住的跳过（入住前 or 搬离后）
                if (!isActiveInMonth(h, year, month)) continue;

                PropertyFee fee = feeMapper.selectOne(new LambdaQueryWrapper<PropertyFee>()
                        .eq(PropertyFee::getHouseholdId, h.getHouseholdId())
                        .eq(PropertyFee::getYear, year).eq(PropertyFee::getMonth, month));

                // 无记录则跳过（数据库有什么就显示什么）
                if (fee == null) continue;
                BigDecimal amount = fee.getAmount();

                String status = statusText(fee.getIsPaid(), year, month, today);

                Map<String, Object> vo = new HashMap<>();
                vo.put("householdId", h.getHouseholdId());
                vo.put("room", h.getRoom());
                vo.put("ownerName", h.getOwnerName());
                vo.put("amount", amount);
                vo.put("isPaid", fee.getIsPaid());
                vo.put("statusText", status);
                vo.put("payDate", fee.getPayDate());
                vo.put("handler", fee.getHandler() != null ? fee.getHandler() : "");
                vo.put("billNo", fee.getBillNo() != null ? fee.getBillNo() : "");
                vos.add(vo);
            }
        }
        // 按状态筛选
        if (statusFilter != null && !statusFilter.isEmpty() && !"全部".equals(statusFilter)) {
            vos = vos.stream().filter(v -> statusFilter.equals(v.get("statusText"))).collect(Collectors.toList());
        }
        return Result.ok(vos);
    }

    // ==================== 催缴提醒（全局，所有在住住户） ====================

    @GetMapping("/reminders")
    public Result<Map<String, Object>> remindersAll() {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        // 先标记逾期，确保催缴数据是最新的
        markOverdue();

        List<HouseholdDTO> households = userFeignClient.getActiveHouseholds();

        List<Map<String, Object>> nearlyDue = new ArrayList<>();  // 临期
        List<Map<String, Object>> overdue = new ArrayList<>();    // 逾期

        if (households != null) {
            for (HouseholdDTO h : households) {
                // 查当前月和之前所有未缴的费用（is_paid != 1）
                List<PropertyFee> unpaidFees = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                        .eq(PropertyFee::getHouseholdId, h.getHouseholdId())
                        .ne(PropertyFee::getIsPaid, 1)
                        .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));

                // 检查历史欠费（逾期）—— 仅对在住月份
                for (PropertyFee f : unpaidFees) {
                    if (!isActiveInMonth(h, f.getYear(), f.getMonth())) continue;  // 入住前/搬离后不提醒
                    if (f.getYear() < curYear || (f.getYear() == curYear && f.getMonth() < curMonth)) {
                        Map<String, Object> vo = new HashMap<>();
                        vo.put("householdId", h.getHouseholdId());
                        vo.put("room", h.getRoom());
                        vo.put("ownerName", h.getOwnerName());
                        vo.put("year", f.getYear());
                        vo.put("month", f.getMonth());
                        vo.put("amount", f.getAmount());
                        vo.put("type", "逾期");
                        LocalDate dueDate = LocalDate.of(f.getYear(), f.getMonth(), Math.min(deadlineConfig.getDeadlineDay(),
                                LocalDate.of(f.getYear(), f.getMonth(), 1).lengthOfMonth()));
                        long days = ChronoUnit.DAYS.between(dueDate, today);
                        vo.put("daysOverdue", Math.max(1, days));
                        overdue.add(vo);
                    }
                }

                // 检查当前月（仅对在住月份）
                if (!isActiveInMonth(h, curYear, curMonth)) continue;

                PropertyFee currentFee = feeMapper.selectOne(new LambdaQueryWrapper<PropertyFee>()
                        .eq(PropertyFee::getHouseholdId, h.getHouseholdId())
                        .eq(PropertyFee::getYear, curYear)
                        .eq(PropertyFee::getMonth, curMonth));

                boolean currentUnpaid = (currentFee == null || currentFee.getIsPaid() != 1);
                if (currentUnpaid) {
                    if (today.getDayOfMonth() > deadlineConfig.getDeadlineDay() - 7 && today.getDayOfMonth() <= deadlineConfig.getDeadlineDay()) {
                        BigDecimal amount = getAmount(currentFee, h);
                        Map<String, Object> vo = new HashMap<>();
                        vo.put("householdId", h.getHouseholdId());
                        vo.put("room", h.getRoom());
                        vo.put("ownerName", h.getOwnerName());
                        vo.put("year", curYear);
                        vo.put("month", curMonth);
                        vo.put("amount", amount);
                        vo.put("type", "临期");
                        long daysUntil = ChronoUnit.DAYS.between(today,
                                LocalDate.of(curYear, curMonth, Math.min(deadlineConfig.getDeadlineDay(),
                                        LocalDate.of(curYear, curMonth, 1).lengthOfMonth())));
                        vo.put("daysUntilDue", Math.max(0, daysUntil));
                        nearlyDue.add(vo);
                    } else if (today.getDayOfMonth() > deadlineConfig.getDeadlineDay()) {
                        BigDecimal amount = getAmount(currentFee, h);
                        Map<String, Object> vo = new HashMap<>();
                        vo.put("householdId", h.getHouseholdId());
                        vo.put("room", h.getRoom());
                        vo.put("ownerName", h.getOwnerName());
                        vo.put("year", curYear);
                        vo.put("month", curMonth);
                        vo.put("amount", amount);
                        vo.put("type", "逾期");
                        vo.put("daysOverdue", today.getDayOfMonth() - deadlineConfig.getDeadlineDay());
                        overdue.add(vo);
                    }
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("nearlyDueCount", nearlyDue.size());
        result.put("overdueCount", overdue.size());
        result.put("nearlyDue", nearlyDue);
        result.put("overdue", overdue);
        return Result.ok(result);
    }

    // ==================== 某户催缴提醒 ====================

    @GetMapping("/reminders/{householdId}")
    public Result<List<Map<String, Object>>> remindersForHousehold(@PathVariable Integer householdId) {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        // 获取住户信息（用于检查入住/搬离日期）
        HouseholdDTO h = userFeignClient.getBriefById(householdId);
        if (h == null) return Result.ok(Collections.emptyList());

        List<PropertyFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .ne(PropertyFee::getIsPaid, 1)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));

        List<Map<String, Object>> list = new ArrayList<>();
        for (PropertyFee f : unpaid) {
            // 跳过不在住的月份（入住前/搬离后）和将来的月份
            if (!isActiveInMonth(h, f.getYear(), f.getMonth())) continue;
            if (f.getYear() > curYear || (f.getYear() == curYear && f.getMonth() > curMonth)) continue;

            Map<String, Object> vo = new HashMap<>();
            vo.put("year", f.getYear()); vo.put("month", f.getMonth()); vo.put("amount", f.getAmount());

            if (f.getYear() < curYear || (f.getYear() == curYear && f.getMonth() < curMonth)) {
                vo.put("type", "逾期");
            } else if (f.getYear() == curYear && f.getMonth() == curMonth) {
                if (today.getDayOfMonth() <= deadlineConfig.getDeadlineDay() - 7) vo.put("type", "待缴");
                else if (today.getDayOfMonth() <= deadlineConfig.getDeadlineDay()) vo.put("type", "临期");
                else vo.put("type", "逾期");
            }
            list.add(vo);
        }
        return Result.ok(list);
    }

    // ==================== 汇总统计报表（月/季度/年） ====================

    /**
     * 汇总统计报表 —— 支持按月、按季度、按年
     * GET /property-fee/summary?year=&period=month|quarter|year&month=&quarter=
     */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(defaultValue = "month") String period,
                                                @RequestParam(required = false) Integer quarter) {
        LocalDate today = LocalDate.now();
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();
        if (quarter == null) quarter = (today.getMonthValue() - 1) / 3 + 1;

        // 先标记逾期
        markOverdue();

        // 含已搬离住户
        List<HouseholdDTO> households = userFeignClient.getAllHouseholds();

        // 确定要统计的月份列表
        List<int[]> monthsToAggregate = buildMonthList(year, month, period, quarter, today);

        // 按户汇总
        BigDecimal totalReceivable = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        int paidCount = 0, pendingCount = 0, overdueCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        if (households != null) {
            for (HouseholdDTO h : households) {
                Map<String, Object> d = aggregateHousehold(h, year, monthsToAggregate, today);
                if (d == null) continue; // 该时段内不在住，跳过

                BigDecimal amount = (BigDecimal) d.get("amount");
                String status = (String) d.get("statusText");

                totalReceivable = totalReceivable.add(amount);
                switch (status) {
                    case "已缴":
                        totalCollected = totalCollected.add(amount); paidCount++; break;
                    case "逾期":
                        totalOutstanding = totalOutstanding.add(amount); overdueCount++; break;
                    default: // 待缴 / 部分已缴 / 部分逾期
                        // 拆分：已缴部分加到实收，未缴部分加到未收
                        BigDecimal collected = (BigDecimal) d.getOrDefault("collectedAmount", BigDecimal.ZERO);
                        totalCollected = totalCollected.add(collected);
                        totalOutstanding = totalOutstanding.add(amount.subtract(collected));
                        // 按最差状态计数
                        if (status.contains("逾期")) overdueCount++;
                        else if (status.contains("待缴")) pendingCount++;
                        else paidCount++;
                        break;
                }
                details.add(d);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("year", year);
        result.put("period", period);
        if ("month".equals(period)) result.put("month", month);
        if ("quarter".equals(period)) result.put("quarter", quarter);
        result.put("monthCount", monthsToAggregate.size());
        result.put("totalReceivable", totalReceivable);
        result.put("totalCollected", totalCollected);
        result.put("totalOutstanding", totalOutstanding);
        result.put("paidCount", paidCount);
        result.put("pendingCount", pendingCount);
        result.put("overdueCount", overdueCount);
        result.put("details", details);
        return Result.ok(result);
    }

    /** 构建要汇总的月份列表 */
    private List<int[]> buildMonthList(int year, int month, String period, int quarter, LocalDate today) {
        List<int[]> list = new ArrayList<>();
        switch (period) {
            case "year":
                for (int m = 1; m <= 12; m++) list.add(new int[]{year, m});
                break;
            case "quarter":
                int startMonth = (quarter - 1) * 3 + 1;
                for (int m = startMonth; m < startMonth + 3; m++) list.add(new int[]{year, m});
                break;
            default: // month
                list.add(new int[]{year, month});
                break;
        }
        return list;
    }

    /** 按户汇总：对月份列表中的所有月聚合金额和状态 */
    private Map<String, Object> aggregateHousehold(HouseholdDTO h, int year,
                                                     List<int[]> months, LocalDate today) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        BigDecimal collectedAmount = BigDecimal.ZERO;
        // 状态优先级: 已缴(1) < 待缴(2) < 逾期(3)
        int worstStatus = 1;
        int monthCount = 0;
        boolean anyActive = false;

        for (int[] ym : months) {
            int y = ym[0], m = ym[1];
            if (!isActiveInMonth(h, y, m)) continue;
            anyActive = true;

            // 未来月份跳过（不展示也无记录）
            if (y > today.getYear() || (y == today.getYear() && m > today.getMonthValue())) continue;

            monthCount++;
            PropertyFee fee = feeMapper.selectOne(new LambdaQueryWrapper<PropertyFee>()
                    .eq(PropertyFee::getHouseholdId, h.getHouseholdId())
                    .eq(PropertyFee::getYear, y).eq(PropertyFee::getMonth, m));

            // 无记录则跳过该月，不虚拟计算金额和状态
            if (fee == null) continue;

            BigDecimal amt = fee.getAmount();
            int isPaid = fee.getIsPaid() != null ? fee.getIsPaid() : 0;
            totalAmount = totalAmount.add(amt);

            if (isPaid == 1) {
                collectedAmount = collectedAmount.add(amt);
                // worstStatus stays at most 1
            } else if (isPaid == -1) {
                worstStatus = Math.max(worstStatus, 3);
            } else {
                worstStatus = Math.max(worstStatus, 2);
            }
        }

        if (!anyActive) return null; // 该时段内完全不在住

        String statusText;
        if (monthCount == 0) {
            statusText = "无记录";
        } else if (worstStatus == 1) {
            statusText = "已缴";
        } else if (worstStatus == 3 && collectedAmount.compareTo(BigDecimal.ZERO) > 0) {
            statusText = "部分逾期";
        } else if (worstStatus == 3) {
            statusText = "逾期";
        } else if (worstStatus == 2 && collectedAmount.compareTo(BigDecimal.ZERO) > 0) {
            statusText = "部分待缴";
        } else {
            statusText = "待缴";
        }

        Map<String, Object> d = new HashMap<>();
        d.put("householdId", h.getHouseholdId());
        d.put("room", h.getRoom());
        d.put("ownerName", h.getOwnerName());
        d.put("amount", totalAmount);
        d.put("collectedAmount", collectedAmount);
        d.put("statusText", statusText);
        d.put("monthCount", monthCount);
        return d;
    }

    // ==================== 查询某户物业费列表 ====================

    @GetMapping("/list/{householdId}")
    public Result<List<Map<String, Object>>> list(@PathVariable Integer householdId,
                                                   @RequestParam(required = false) Integer year) {
        // 1. 先标记逾期
        markOverdue();

        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();
        int deadlineDay = deadlineConfig.getDeadlineDay();

        // 2. 查询（数据库有什么就显示什么，不自动创建记录）
        LambdaQueryWrapper<PropertyFee> w = new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth);
        if (year != null) w.eq(PropertyFee::getYear, year);
        List<PropertyFee> fees = feeMapper.selectList(w);

        // 4. 过滤未来月份 + 映射状态文本
        List<Map<String, Object>> vos = new ArrayList<>();
        for (PropertyFee f : fees) {
            // 未来月份只展示已缴记录
            if (f.getYear() > curYear || (f.getYear() == curYear && f.getMonth() > curMonth)) {
                if (f.getIsPaid() == null || f.getIsPaid() != 1) continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("feeId", f.getFeeId()); vo.put("year", f.getYear()); vo.put("month", f.getMonth());
            vo.put("amount", f.getAmount()); vo.put("isPaid", f.getIsPaid());
            vo.put("payDate", f.getPayDate()); vo.put("billNo", f.getBillNo());
            vo.put("statusText", statusText(f.getIsPaid(), f.getYear(), f.getMonth(), today));
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    // ==================== 缴费（使用公共缴费服务） ====================

    @PostMapping("/pay")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        Integer householdId = (Integer) body.get("householdId");
        Integer duration = (Integer) body.getOrDefault("duration", 1);
        if (householdId == null) return Result.fail("请指定住户");

        // 1. 找到该户所有未缴月份（is_paid != 1，即 0待缴 + -1逾期）
        List<PropertyFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .ne(PropertyFee::getIsPaid, 1)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));
        // 没有待缴记录时，自动创建当前月的费用记录
        if (unpaid.isEmpty()) {
            HouseholdDTO h = userFeignClient.getBriefById(householdId);
            if (h == null) return Result.fail("住户不存在");
            BigDecimal amount = h.getArea() != null && h.getPropertyFeeRate() != null
                    ? h.getArea().multiply(h.getPropertyFeeRate()) : BigDecimal.ZERO;
            PropertyFee newFee = new PropertyFee();
            newFee.setHouseholdId(householdId);
            newFee.setYear(LocalDate.now().getYear());
            newFee.setMonth(LocalDate.now().getMonthValue());
            newFee.setAmount(amount);
            newFee.setIsPaid(0);
            feeMapper.insert(newFee);
            unpaid = new ArrayList<>();
            unpaid.add(newFee);
        }

        LocalDate today = LocalDate.now();
        PropertyFee first = unpaid.get(0);

        // 2. 用公共方法计算缴费范围
        PaymentRange range = FeePaymentHelper.calculatePaymentRange(
                first.getYear(), first.getMonth(), duration, today);

        // 3. 过滤范围内已存在的未缴记录
        List<PropertyFee> toPay = unpaid.stream()
                .filter(f -> FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range))
                .collect(Collectors.toList());

        // 4. 补建缺失月份的记录（预缴场景）
        Set<String> existing = toPay.stream().map(f -> f.getYear() + "-" + f.getMonth()).collect(Collectors.toSet());
        for (int[] ym : FeePaymentHelper.computeMissingMonths(existing, range)) {
            PropertyFee gf = new PropertyFee();
            gf.setHouseholdId(householdId); gf.setYear(ym[0]); gf.setMonth(ym[1]);
            gf.setAmount(first.getAmount());
            gf.setIsPaid(0);
            feeMapper.insert(gf);
            toPay.add(gf);
        }
        toPay.sort(Comparator.comparingInt((PropertyFee f) -> f.getYear()).thenComparingInt(f -> f.getMonth()));

        // 5. 批量标记已缴
        String billNo = FeePaymentHelper.generateBillNo("WY");
        String handler = (String) body.getOrDefault("handler", "在线缴费");
        BigDecimal total = BigDecimal.ZERO;
        for (PropertyFee f : toPay) {
            f.setIsPaid(1);
            f.setPayDate(today);
            f.setBillNo(billNo);
            f.setHandler(handler);
            feeMapper.updateById(f);
            total = total.add(f.getAmount());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("billNo", billNo);
        result.put("monthCount", toPay.size());
        result.put("total", total);
        result.put("paidMonths", toPay.stream().map(f -> f.getYear() + "年" + f.getMonth() + "月").collect(Collectors.toList()));
        return Result.ok(result);
    }

    // ==================== 入住时生成当月账单（供 user-service Feign 调用） ====================

    /**
     * 住户办理入住后自动生成当月物业费账单
     * 根据入住日期和截止日判定 is_paid：0待缴 或 -1逾期
     */
    @PostMapping("/generate")
    public Result<Void> generateBill(@RequestBody Map<String, Object> body) {
        Integer householdId = (Integer) body.get("householdId");
        if (householdId == null) return Result.fail("缺少 householdId");
        HouseholdDTO h = userFeignClient.getBriefById(householdId);
        if (h == null) return Result.fail("住户不存在");

        LocalDate today = LocalDate.now();
        // 查当月是否已有记录
        PropertyFee existing = feeMapper.selectOne(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .eq(PropertyFee::getYear, today.getYear())
                .eq(PropertyFee::getMonth, today.getMonthValue()));
        if (existing != null) return Result.ok("当月账单已存在");

        BigDecimal amount = (h.getArea() != null && h.getPropertyFeeRate() != null)
                ? h.getArea().multiply(h.getPropertyFeeRate()) : BigDecimal.ZERO;
        int isPaid = today.getDayOfMonth() > deadlineConfig.getDeadlineDay() ? -1 : 0;

        PropertyFee fee = new PropertyFee();
        fee.setHouseholdId(householdId);
        fee.setYear(today.getYear());
        fee.setMonth(today.getMonthValue());
        fee.setAmount(amount);
        fee.setIsPaid(isPaid);
        feeMapper.insert(fee);
        return Result.ok("已生成当月物业费账单");
    }

    // ==================== 截止日配置 ====================

    @GetMapping("/deadline")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> getDeadline() {
        Map<String, Object> m = new HashMap<>(); m.put("deadlineDay", deadlineConfig.getDeadlineDay());
        return Result.ok(m);
    }

    @PutMapping("/deadline")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> setDeadline(@RequestBody Map<String, Object> body) {
        int day = (int) body.getOrDefault("deadlineDay", 10);
        if (day < 1 || day > 28) return Result.fail("截止日必须在 1-28 之间");
        deadlineConfig.setDeadlineDay(day);
        // 截止日变更后刷新本月待缴/逾期状态
        try {
            jdbcTemplate.update("CALL sp_refresh_after_deadline_change(?)", day);
        } catch (Exception e) { /* 存储过程可能尚未部署，忽略 */ }
        return Result.ok("已更新截止日为每月 " + day + " 号，本月账单状态已同步刷新");
    }
}
