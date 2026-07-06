package com.community.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.common.dto.HouseholdDTO;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.config.DeadlineConfig;
import com.community.property.feign.UserServiceFeignClient;
import com.community.property.mapper.ParkingSpaceMapper;
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
 * 停车费管理（逻辑同物业费，状态仅三种：已缴/待缴/逾期，默认截止日每月1号）
 */
@RestController
@RequestMapping("/parking-fee")
public class ParkingFeeController {

    @Resource private ParkingFeeMapper feeMapper;
    @Resource private ParkingSpaceMapper spaceMapper;
    @Resource private UserServiceFeignClient userFeignClient;
    @Resource private DeadlineConfig deadlineConfig;
    @Resource private JdbcTemplate jdbcTemplate;

    // ==================== 状态判定 ====================

    private void markOverdue() {
        try {
            jdbcTemplate.update("CALL sp_mark_overdue(?)", deadlineConfig.getDeadlineDay());
        } catch (Exception e) { /* 存储过程可能尚未部署 */ }
    }

    /** is_paid 三态 → 展示文本（含"历史记录"判断） */
    private String statusText(ParkingFee fee, int year, int month, LocalDate today, LocalDate assignedDate) {
        if (fee != null && fee.getIsPaid() != null && fee.getIsPaid() == 1) {
            if (year > today.getYear() || (year == today.getYear() && month > today.getMonthValue()))
                return "提前缴费";
            return "已缴";
        }
        // 分配前的记录标记为"历史记录"
        if (assignedDate != null
                && (year < assignedDate.getYear() || (year == assignedDate.getYear() && month < assignedDate.getMonthValue())))
            return "历史记录";
        if (fee != null && fee.getIsPaid() != null && fee.getIsPaid() == -1) return "逾期";
        if (year < today.getYear() || (year == today.getYear() && month < today.getMonthValue())) return "逾期";
        // 当前月：即使 is_paid=0，过了截止日也应显示逾期（与存储过程判定一致）
        if (year == today.getYear() && month == today.getMonthValue()
                && today.getDayOfMonth() > deadlineConfig.getDeadlineDay())
            return "逾期";
        return "待缴";
    }

    /** 判断车位在指定月份是否已有租户分配（只从分配月起计费） */
    private boolean isAssignedInMonth(ParkingSpace s, int year, int month) {
        if (s.getAssignedDate() == null) return true; // 旧数据兼容：无分配日期视为一直分配
        if (year < s.getAssignedDate().getYear()) return false;
        if (year == s.getAssignedDate().getYear() && month < s.getAssignedDate().getMonthValue()) return false;
        return true;
    }

    // ==================== 汇总统计 ====================

    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(@RequestParam(required = false) Integer year,
                                                @RequestParam(required = false) Integer month,
                                                @RequestParam(required = false) String statusFilter) {
        LocalDate today = LocalDate.now();
        if (year == null) year = today.getYear();
        if (month == null) month = today.getMonthValue();

        // 先标记逾期，确保汇总统计基于最新状态
        markOverdue();

        // 查所有车位（已租 + 空闲），历史缴费记录也要显示
        List<ParkingSpace> spaces = spaceMapper.selectList(null);
        BigDecimal totalReceivable = BigDecimal.ZERO, totalCollected = BigDecimal.ZERO, totalOutstanding = BigDecimal.ZERO;
        int paidCount = 0, pendingCount = 0, overdueCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (ParkingSpace s : spaces) {
            // 先查DB：有记录就始终显示（可能来自前租户）
            ParkingFee fee = feeMapper.selectOne(new LambdaQueryWrapper<ParkingFee>()
                    .eq(ParkingFee::getSpaceNo, s.getSpaceNo())
                    .eq(ParkingFee::getYear, year).eq(ParkingFee::getMonth, month));
            // 无DB记录 +（空闲或无当前租户 或 未分配到该月）→ 跳过
            if (fee == null && (s.getHouseholdId() == null || !isAssignedInMonth(s, year, month))) continue;

            // 自动补建缺失记录（与 list() / 物业费 report() 行为一致）
            BigDecimal amount;
            if (fee == null) {
                // 未来月份不自动创建记录，也不展示（除非已预缴，则 fee != null 不进入此分支）
                if (year > today.getYear() || (year == today.getYear() && month > today.getMonthValue())) {
                    continue;
                }
                amount = s.getMonthlyFee() != null ? s.getMonthlyFee() : BigDecimal.ZERO;
                int initPaid = 0;
                if (year < today.getYear() || (year == today.getYear() && month < today.getMonthValue())) {
                    initPaid = -1;
                } else if (year == today.getYear() && month == today.getMonthValue()
                        && today.getDayOfMonth() > deadlineConfig.getDeadlineDay()) {
                    initPaid = -1;
                }
                ParkingFee newFee = new ParkingFee();
                newFee.setSpaceNo(s.getSpaceNo());
                newFee.setHouseholdId(s.getHouseholdId());
                newFee.setYear(year); newFee.setMonth(month);
                newFee.setAmount(amount); newFee.setIsPaid(initPaid);
                feeMapper.insert(newFee);
                fee = newFee;
            } else {
                amount = fee.getAmount();
            }

            String status = statusText(fee, year, month, today, s.getAssignedDate());
            totalReceivable = totalReceivable.add(amount);
            switch (status) {
                case "已缴": case "提前缴费": totalCollected = totalCollected.add(amount); paidCount++; break;
                case "逾期": totalOutstanding = totalOutstanding.add(amount); overdueCount++; break;
                case "待缴": case "历史记录": totalOutstanding = totalOutstanding.add(amount); pendingCount++; break;
            }

            // 查询户主信息：有DB记录用fee中的household_id（冻结的），否则用space当前的
            String ownerName = "", room = "";
            Integer lookupId = (fee != null) ? fee.getHouseholdId() : s.getHouseholdId();
            if (lookupId != null) {
                HouseholdDTO h = userFeignClient.getBriefById(lookupId);
                if (h != null) { ownerName = h.getOwnerName(); room = h.getRoom(); }
            }

            Map<String, Object> d = new HashMap<>();
            d.put("spaceNo", s.getSpaceNo()); d.put("plateNo", s.getPlateNo());
            d.put("ownerName", ownerName); d.put("room", room);
            d.put("amount", amount); d.put("statusText", status);
            d.put("payDate", fee != null ? fee.getPayDate() : null);
            d.put("handler", fee != null ? fee.getHandler() : "");
            d.put("billNo", fee != null ? fee.getBillNo() : "");
            details.add(d);
        }

        // 按状态筛选
        if (statusFilter != null && !statusFilter.isEmpty() && !"全部".equals(statusFilter)) {
            details = details.stream().filter(d -> statusFilter.equals(d.get("statusText"))).collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("year", year); result.put("month", month);
        result.put("totalSpaces", details.size());
        result.put("totalReceivable", totalReceivable);
        result.put("totalCollected", totalCollected);
        result.put("totalOutstanding", totalOutstanding);
        result.put("paidCount", paidCount);
        result.put("pendingCount", pendingCount);
        result.put("overdueCount", overdueCount);
        result.put("details", details);
        return Result.ok(result);
    }

    // ==================== 催缴提醒（全局） ====================

    @GetMapping("/reminders")
    public Result<Map<String, Object>> remindersAll() {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        // 先标记逾期，确保催缴数据是最新的
        markOverdue();

        List<ParkingSpace> spaces = spaceMapper.selectList(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getStatus, 1).isNotNull(ParkingSpace::getHouseholdId));
        List<Map<String, Object>> nearlyDue = new ArrayList<>();
        List<Map<String, Object>> overdue = new ArrayList<>();

        for (ParkingSpace s : spaces) {
            List<ParkingFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                    .eq(ParkingFee::getSpaceNo, s.getSpaceNo())
                    .ne(ParkingFee::getIsPaid, 1)
                    .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));

            // 历史逾期
            for (ParkingFee f : unpaid) {
                if (f.getYear() < curYear || (f.getYear() == curYear && f.getMonth() < curMonth)) {
                    // 跳过分配前月份的费用记录
                    if (!isAssignedInMonth(s, f.getYear(), f.getMonth())) continue;
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("spaceNo", s.getSpaceNo()); vo.put("plateNo", s.getPlateNo());
                    vo.put("year", f.getYear()); vo.put("month", f.getMonth());
                    vo.put("amount", f.getAmount()); vo.put("type", "逾期");
                    LocalDate dueDate = LocalDate.of(f.getYear(), f.getMonth(), Math.min(deadlineConfig.getDeadlineDay(),
                            LocalDate.of(f.getYear(), f.getMonth(), 1).lengthOfMonth()));
                    vo.put("daysOverdue", Math.max(1, ChronoUnit.DAYS.between(dueDate, today)));
                    overdue.add(vo);
                }
            }

            // 当前月（仅检查分配后的车位）
            if (!isAssignedInMonth(s, curYear, curMonth)) continue;
            ParkingFee currentFee = feeMapper.selectOne(new LambdaQueryWrapper<ParkingFee>()
                    .eq(ParkingFee::getSpaceNo, s.getSpaceNo())
                    .eq(ParkingFee::getYear, curYear)
                    .eq(ParkingFee::getMonth, curMonth));
            boolean currentUnpaid = (currentFee == null || currentFee.getIsPaid() != 1);
            if (currentUnpaid) {
                BigDecimal amount = (currentFee != null) ? currentFee.getAmount()
                        : (s.getMonthlyFee() != null ? s.getMonthlyFee() : BigDecimal.ZERO);
                if (today.getDayOfMonth() > deadlineConfig.getDeadlineDay() - 7 && today.getDayOfMonth() <= deadlineConfig.getDeadlineDay()) {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("spaceNo", s.getSpaceNo()); vo.put("plateNo", s.getPlateNo());
                    vo.put("year", curYear); vo.put("month", curMonth);
                    vo.put("amount", amount); vo.put("type", "临期");
                    vo.put("daysUntilDue", Math.max(0, deadlineConfig.getDeadlineDay() - today.getDayOfMonth()));
                    nearlyDue.add(vo);
                } else if (today.getDayOfMonth() > deadlineConfig.getDeadlineDay()) {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("spaceNo", s.getSpaceNo()); vo.put("plateNo", s.getPlateNo());
                    vo.put("year", curYear); vo.put("month", curMonth);
                    vo.put("amount", amount); vo.put("type", "逾期");
                    vo.put("daysOverdue", today.getDayOfMonth() - deadlineConfig.getDeadlineDay());
                    overdue.add(vo);
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

    // ==================== 查询某车位停车费 ====================

    @GetMapping("/list/{spaceNo}")
    public Result<List<Map<String, Object>>> list(@PathVariable String spaceNo, @RequestParam(required = false) Integer year) {
        // 1. 标记逾期
        markOverdue();

        ParkingSpace s = spaceMapper.selectById(spaceNo);
        LocalDate assignedDate = s != null ? s.getAssignedDate() : null;
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        // 2. 自动补当月记录（始终补，截止日影响 is_paid 初值）
        if (s != null && s.getHouseholdId() != null && isAssignedInMonth(s, curYear, curMonth)) {
            ParkingFee curFee = feeMapper.selectOne(new LambdaQueryWrapper<ParkingFee>()
                    .eq(ParkingFee::getSpaceNo, spaceNo)
                    .eq(ParkingFee::getYear, curYear).eq(ParkingFee::getMonth, curMonth));
            if (curFee == null) {
                int deadlineDay = deadlineConfig.getDeadlineDay();
                int initPaid = today.getDayOfMonth() <= deadlineDay ? 0 : -1;
                ParkingFee newFee = new ParkingFee();
                newFee.setSpaceNo(spaceNo); newFee.setHouseholdId(s.getHouseholdId());
                newFee.setYear(curYear); newFee.setMonth(curMonth);
                newFee.setAmount(s.getMonthlyFee()); newFee.setIsPaid(initPaid);
                feeMapper.insert(newFee);
            }
        }

        // 3. 查询（过滤未来月：只返回已缴费的）
        LambdaQueryWrapper<ParkingFee> w = new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getSpaceNo, spaceNo).orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth);
        if (year != null) w.eq(ParkingFee::getYear, year);
        List<ParkingFee> fees = feeMapper.selectList(w);
        List<Map<String, Object>> vos = new ArrayList<>();
        for (ParkingFee f : fees) {
            // 未来月份只展示已缴记录
            if (f.getYear() > curYear || (f.getYear() == curYear && f.getMonth() > curMonth)) {
                if (f.getIsPaid() == null || f.getIsPaid() != 1) continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("feeId", f.getFeeId()); vo.put("spaceNo", f.getSpaceNo());
            vo.put("year", f.getYear()); vo.put("month", f.getMonth());
            vo.put("amount", f.getAmount()); vo.put("isPaid", f.getIsPaid());
            vo.put("payDate", f.getPayDate()); vo.put("billNo", f.getBillNo());
            vo.put("statusText", statusText(f, f.getYear(), f.getMonth(), today, assignedDate));
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    // ==================== 缴费（使用公共缴费服务） ====================

    @PostMapping("/pay")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> pay(@RequestBody Map<String, Object> body) {
        String spaceNo = (String) body.get("spaceNo");
        Integer duration = (Integer) body.getOrDefault("duration", 1);
        if (spaceNo == null) return Result.fail("请指定车位");

        // 1. 找到该车位所有未缴月份（is_paid != 1，即 0待缴 + -1逾期）
        List<ParkingFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getSpaceNo, spaceNo).ne(ParkingFee::getIsPaid, 1)
                .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));
        // 没有待缴记录时，自动创建当前月的费用记录
        if (unpaid.isEmpty()) {
            ParkingSpace s = spaceMapper.selectById(spaceNo);
            if (s == null) return Result.fail("车位不存在");
            if (s.getHouseholdId() == null) return Result.fail("该车位尚未分配给住户，无法缴费");
            BigDecimal amount = s.getMonthlyFee() != null ? s.getMonthlyFee() : BigDecimal.ZERO;
            ParkingFee newFee = new ParkingFee();
            newFee.setSpaceNo(spaceNo);
            newFee.setHouseholdId(s.getHouseholdId());
            newFee.setYear(LocalDate.now().getYear());
            newFee.setMonth(LocalDate.now().getMonthValue());
            newFee.setAmount(amount);
            newFee.setIsPaid(0);
            feeMapper.insert(newFee);
            unpaid = new ArrayList<>();
            unpaid.add(newFee);
            // 确保返回的 first 引用指向新记录
        }

        LocalDate today = LocalDate.now();
        ParkingFee first = unpaid.get(0);

        // 2. 公用方法计算缴费范围（逻辑同物业费）
        PaymentRange range = FeePaymentHelper.calculatePaymentRange(
                first.getYear(), first.getMonth(), duration, today);

        // 3. 过滤范围内已存在的未缴记录
        List<ParkingFee> toPay = unpaid.stream()
                .filter(f -> FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range))
                .collect(Collectors.toList());

        // 4. 补建缺失月份（预缴场景）
        Set<String> existing = toPay.stream().map(f -> f.getYear() + "-" + f.getMonth()).collect(Collectors.toSet());
        for (int[] ym : FeePaymentHelper.computeMissingMonths(existing, range)) {
            ParkingFee gf = new ParkingFee();
            gf.setSpaceNo(spaceNo); gf.setYear(ym[0]); gf.setMonth(ym[1]);
            gf.setHouseholdId(first.getHouseholdId());  // 必须设置，数据库 NOT NULL
            gf.setAmount(first.getAmount());
            gf.setIsPaid(0);
            feeMapper.insert(gf);
            toPay.add(gf);
        }
        toPay.sort(Comparator.comparingInt((ParkingFee f) -> f.getYear()).thenComparingInt(f -> f.getMonth()));

        // 5. 批量标记已缴
        String billNo = FeePaymentHelper.generateBillNo("TC");
        String handler = (String) body.getOrDefault("handler", "在线缴费");
        BigDecimal total = BigDecimal.ZERO;
        for (ParkingFee f : toPay) {
            f.setIsPaid(1); f.setPayDate(today); f.setBillNo(billNo); f.setHandler(handler);
            feeMapper.updateById(f);
            total = total.add(f.getAmount());
        }
        Map<String, Object> r = new HashMap<>();
        r.put("billNo", billNo); r.put("monthCount", toPay.size()); r.put("total", total);
        return Result.ok(r);
    }

    // ==================== 截止日配置（与物业费共用） ====================

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
        try {
            jdbcTemplate.update("CALL sp_refresh_after_deadline_change(?)", day);
        } catch (Exception e) { /* 忽略 */ }
        return Result.ok("停车费截止日已更新为每月 " + day + " 号，本月账单状态已同步刷新");
    }
}
