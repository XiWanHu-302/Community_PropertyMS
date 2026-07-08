package com.community.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.dto.HouseholdDTO;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.entity.PropertyFee;
import com.community.property.feign.UserServiceFeignClient;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.mapper.ParkingSpaceMapper;
import com.community.property.mapper.PropertyFeeMapper;
import com.community.property.service.FeePaymentHelper.PaymentRange;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 物业费业务逻辑
 * <p>
 * 原则：数据库是唯一真相来源。不补建缺失月份，不调用全局逾期标记。
 * 所有费用记录由定时任务（每月1日）和入住时即时生成保障完整性。
 */
@Service
public class PropertyFeeService {

    @Resource private PropertyFeeMapper feeMapper;
    @Resource private ParkingFeeMapper parkingFeeMapper;
    @Resource private ParkingSpaceMapper parkingSpaceMapper;
    @Resource private ParkingFeeQueryService parkingFeeQueryService;
    @Resource private UserServiceFeignClient userFeignClient;
    @Resource private FeeDeadlineService deadlineService;

    // ==================== 搬离前未缴检查 ====================

    /** 只查数据库中已有的待缴/逾期记录 */
    public Map<String, Object> unpaidCheck(Integer householdId) {
        HouseholdDTO h = userFeignClient.getBriefById(householdId);
        if (h == null) throw new RuntimeException("住户不存在");

        // 物业费未缴（is_paid != 1）
        List<PropertyFee> propUnpaid = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .ne(PropertyFee::getIsPaid, 1)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));

        // 停车费未缴（只查数据库真实记录）
        Map<String, Object> parkData = parkingFeeQueryService.getParkingUnpaidForHousehold(householdId);
        @SuppressWarnings("unchecked")
        List<ParkingFee> parkUnpaid = (List<ParkingFee>) parkData.get("unpaidList");

        BigDecimal propTotal = propUnpaid.stream().map(PropertyFee::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal parkTotal = (BigDecimal) parkData.get("totalUnpaid");

        List<Map<String, Object>> details = new ArrayList<>();
        for (PropertyFee f : propUnpaid) {
            Map<String, Object> d = new HashMap<>();
            d.put("type", "物业费"); d.put("year", f.getYear()); d.put("month", f.getMonth());
            d.put("amount", f.getAmount()); details.add(d);
        }
        for (ParkingFee f : parkUnpaid) {
            Map<String, Object> d = new HashMap<>();
            d.put("type", "停车费"); d.put("spaceNo", f.getSpaceNo());
            d.put("year", f.getYear()); d.put("month", f.getMonth());
            d.put("amount", f.getAmount()); details.add(d);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("householdId", householdId);
        result.put("propTotal", propTotal);
        result.put("parkTotal", parkTotal);
        result.put("totalUnpaid", propTotal.add(parkTotal));
        result.put("canMoveOut", details.isEmpty());
        result.put("details", details);
        return result;
    }

    public Map<String, Object> unpaidCheckRaw(Integer householdId) {
        return unpaidCheck(householdId);
    }

    // ==================== 月报表 ====================

    /** 直接从数据库查询，不补建 */
    public List<Map<String, Object>> report(Integer year, Integer month, String statusFilter) {
        List<HouseholdDTO> households = userFeignClient.getAllHouseholds();
        if (households == null || households.isEmpty()) return Collections.emptyList();

        List<Integer> activeIds = households.stream()
                .filter(h -> isActiveInMonth(h, year, month))
                .map(HouseholdDTO::getHouseholdId).collect(Collectors.toList());

        // 批量查询（修复 N+1）
        Map<Integer, PropertyFee> feeMap = Collections.emptyMap();
        if (!activeIds.isEmpty()) {
            List<PropertyFee> allFees = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                    .eq(PropertyFee::getYear, year).eq(PropertyFee::getMonth, month)
                    .in(PropertyFee::getHouseholdId, activeIds));
            feeMap = allFees.stream().collect(Collectors.toMap(PropertyFee::getHouseholdId, Function.identity()));
        }

        List<Map<String, Object>> vos = new ArrayList<>();
        for (HouseholdDTO h : households) {
            if (!isActiveInMonth(h, year, month)) continue;
            PropertyFee fee = feeMap.get(h.getHouseholdId());
            if (fee == null) continue;

            Map<String, Object> vo = new HashMap<>();
            vo.put("householdId", h.getHouseholdId());
            vo.put("room", h.getRoom()); vo.put("ownerName", h.getOwnerName());
            vo.put("amount", fee.getAmount()); vo.put("isPaid", fee.getIsPaid());
            vo.put("statusText", FeePaymentHelper.feeStatusText(fee.getIsPaid()));
            vo.put("payDate", fee.getPayDate());
            vo.put("handler", fee.getHandler() != null ? fee.getHandler() : "");
            vo.put("billNo", fee.getBillNo() != null ? fee.getBillNo() : "");
            vos.add(vo);
        }

        if (statusFilter != null && !statusFilter.isEmpty() && !"全部".equals(statusFilter)) {
            vos = vos.stream().filter(v -> statusFilter.equals(v.get("statusText"))).collect(Collectors.toList());
        }
        return vos;
    }

    // ==================== 单户催缴提醒 ====================

    /**
     * 逾期：直接读数据库 is_paid = -1；临期：截止日前7天内做日期判断
     * <p>
     * 返回结构化数据，前端期望：{overdueCount, nearlyDueCount, overdue: [{feeType, year, month, amount, daysOverdue}], nearlyDue: [...]}
     */
    public Map<String, Object> remindersForHousehold(Integer householdId) {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();
        int deadlineDay = deadlineService.getDeadlineDay();

        HouseholdDTO h = userFeignClient.getBriefById(householdId);
        if (h == null) {
            return buildRemindersResult(Collections.emptyList(), Collections.emptyList());
        }

        List<PropertyFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .ne(PropertyFee::getIsPaid, 1)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));

        List<Map<String, Object>> overdue = new ArrayList<>();
        List<Map<String, Object>> nearlyDue = new ArrayList<>();

        for (PropertyFee f : unpaid) {
            if (!isActiveInMonth(h, f.getYear(), f.getMonth())) continue;
            if (f.getYear() > curYear || (f.getYear() == curYear && f.getMonth() > curMonth)) continue;

            if (f.getIsPaid() != null && f.getIsPaid() == -1) {
                // 数据库标注逾期
                Map<String, Object> vo = new HashMap<>();
                vo.put("feeType", "物业费");
                vo.put("year", f.getYear()); vo.put("month", f.getMonth()); vo.put("amount", f.getAmount());
                LocalDate dueDate = LocalDate.of(f.getYear(), f.getMonth(),
                        Math.min(deadlineDay, LocalDate.of(f.getYear(), f.getMonth(), 1).lengthOfMonth()));
                vo.put("daysOverdue", Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(dueDate, today)));
                overdue.add(vo);
            } else if (f.getYear() == curYear && f.getMonth() == curMonth) {
                // 当前月：根据截止日做日期判断
                if (today.getDayOfMonth() > deadlineDay - 7 && today.getDayOfMonth() <= deadlineDay) {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("feeType", "物业费");
                    vo.put("year", f.getYear()); vo.put("month", f.getMonth()); vo.put("amount", f.getAmount());
                    vo.put("daysUntilDue", Math.max(0, deadlineDay - today.getDayOfMonth()));
                    nearlyDue.add(vo);
                } else if (today.getDayOfMonth() > deadlineDay) {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("feeType", "物业费");
                    vo.put("year", f.getYear()); vo.put("month", f.getMonth()); vo.put("amount", f.getAmount());
                    vo.put("daysOverdue", today.getDayOfMonth() - deadlineDay);
                    overdue.add(vo);
                }
                // today <= deadlineDay - 7：待缴状态，不提醒
            }
            // 历史月份 is_paid=0（异常情况）：不提醒
        }

        // ---- 停车费催缴 ----
        List<ParkingSpace> mySpaces = parkingSpaceMapper.selectList(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getHouseholdId, householdId));
        if (!mySpaces.isEmpty()) {
            List<String> spaceNos = mySpaces.stream().map(ParkingSpace::getSpaceNo).collect(Collectors.toList());
            List<ParkingFee> parkUnpaid = parkingFeeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                    .in(ParkingFee::getSpaceNo, spaceNos)
                    .ne(ParkingFee::getIsPaid, 1)
                    .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));

            for (ParkingFee pf : parkUnpaid) {
                if (pf.getYear() > curYear || (pf.getYear() == curYear && pf.getMonth() > curMonth)) continue;

                if (pf.getIsPaid() != null && pf.getIsPaid() == -1) {
                    Map<String, Object> vo = new HashMap<>();
                    vo.put("feeType", "停车费(" + pf.getSpaceNo() + ")");
                    vo.put("year", pf.getYear()); vo.put("month", pf.getMonth()); vo.put("amount", pf.getAmount());
                    LocalDate dueDate = LocalDate.of(pf.getYear(), pf.getMonth(),
                            Math.min(deadlineDay, LocalDate.of(pf.getYear(), pf.getMonth(), 1).lengthOfMonth()));
                    vo.put("daysOverdue", Math.max(1, java.time.temporal.ChronoUnit.DAYS.between(dueDate, today)));
                    overdue.add(vo);
                } else if (pf.getYear() == curYear && pf.getMonth() == curMonth) {
                    if (today.getDayOfMonth() > deadlineDay - 7 && today.getDayOfMonth() <= deadlineDay) {
                        Map<String, Object> vo = new HashMap<>();
                        vo.put("feeType", "停车费(" + pf.getSpaceNo() + ")");
                        vo.put("year", pf.getYear()); vo.put("month", pf.getMonth()); vo.put("amount", pf.getAmount());
                        vo.put("daysUntilDue", Math.max(0, deadlineDay - today.getDayOfMonth()));
                        nearlyDue.add(vo);
                    } else if (today.getDayOfMonth() > deadlineDay) {
                        Map<String, Object> vo = new HashMap<>();
                        vo.put("feeType", "停车费(" + pf.getSpaceNo() + ")");
                        vo.put("year", pf.getYear()); vo.put("month", pf.getMonth()); vo.put("amount", pf.getAmount());
                        vo.put("daysOverdue", today.getDayOfMonth() - deadlineDay);
                        overdue.add(vo);
                    }
                }
            }
        }

        return buildRemindersResult(nearlyDue, overdue);
    }

    private Map<String, Object> buildRemindersResult(List<Map<String, Object>> nearlyDue,
                                                      List<Map<String, Object>> overdue) {
        Map<String, Object> result = new HashMap<>();
        result.put("overdueCount", overdue.size());
        result.put("nearlyDueCount", nearlyDue.size());
        result.put("overdue", overdue);
        result.put("nearlyDue", nearlyDue);
        return result;
    }

    // ==================== 汇总统计 ====================

    public Map<String, Object> summary(Integer year, Integer month, String period, Integer quarter) {
        LocalDate today = LocalDate.now();
        List<HouseholdDTO> households = userFeignClient.getAllHouseholds();
        List<int[]> monthsToAggregate = buildMonthList(year, month, period, quarter);

        // 批量查询所有涉及月份的 fee（修复 N+1）
        Map<String, PropertyFee> feeIndex = new HashMap<>();
        for (int[] ym : monthsToAggregate) {
            List<PropertyFee> monthFees = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                    .eq(PropertyFee::getYear, ym[0]).eq(PropertyFee::getMonth, ym[1]));
            for (PropertyFee f : monthFees) {
                feeIndex.put(f.getHouseholdId() + "-" + f.getYear() + "-" + f.getMonth(), f);
            }
        }

        BigDecimal totalReceivable = BigDecimal.ZERO, totalCollected = BigDecimal.ZERO, totalOutstanding = BigDecimal.ZERO;
        int paidCount = 0, pendingCount = 0, overdueCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        if (households != null) {
            for (HouseholdDTO h : households) {
                Map<String, Object> d = aggregateHousehold(h, monthsToAggregate, today, feeIndex);
                if (d == null) continue;

                BigDecimal amount = (BigDecimal) d.get("amount");
                String status = (String) d.get("statusText");
                totalReceivable = totalReceivable.add(amount);

                switch (status) {
                    case "已缴":
                        totalCollected = totalCollected.add(amount); paidCount++; break;
                    case "逾期":
                        totalOutstanding = totalOutstanding.add(amount); overdueCount++; break;
                    default:
                        BigDecimal collected = (BigDecimal) d.getOrDefault("collectedAmount", BigDecimal.ZERO);
                        totalCollected = totalCollected.add(collected);
                        totalOutstanding = totalOutstanding.add(amount.subtract(collected));
                        if (status.contains("逾期")) overdueCount++;
                        else if (status.contains("待缴")) pendingCount++;
                        else paidCount++;
                        break;
                }
                details.add(d);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("year", year); result.put("period", period);
        if ("month".equals(period)) result.put("month", month);
        if ("quarter".equals(period)) result.put("quarter", quarter);
        result.put("monthCount", monthsToAggregate.size());
        result.put("totalReceivable", totalReceivable);
        result.put("totalCollected", totalCollected);
        result.put("totalOutstanding", totalOutstanding);
        result.put("paidCount", paidCount); result.put("pendingCount", pendingCount);
        result.put("overdueCount", overdueCount); result.put("details", details);
        return result;
    }

    // ==================== 查询某户物业费列表 ====================

    public List<Map<String, Object>> listFees(Integer householdId, Integer year) {
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        LambdaQueryWrapper<PropertyFee> w = new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth);
        if (year != null) w.eq(PropertyFee::getYear, year);
        List<PropertyFee> fees = feeMapper.selectList(w);

        List<Map<String, Object>> vos = new ArrayList<>();
        for (PropertyFee f : fees) {
            if (f.getYear() > curYear || (f.getYear() == curYear && f.getMonth() > curMonth)) {
                if (f.getIsPaid() == null || f.getIsPaid() != 1) continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("feeId", f.getFeeId()); vo.put("year", f.getYear()); vo.put("month", f.getMonth());
            vo.put("amount", f.getAmount()); vo.put("isPaid", f.getIsPaid());
            vo.put("payDate", f.getPayDate()); vo.put("billNo", f.getBillNo());
            vo.put("statusText", FeePaymentHelper.feeStatusText(f.getIsPaid()));
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 缴费 ====================

    /**
     * 缴费：
     * 1. 现有未缴记录（≤当前月）→ 标记已缴
     * 2. 缴费范围内缺少的未来月份（>当前月）→ 创建并标记已缴（预缴）
     * 3. 所有未缴已清时，从下个月开始纯预缴
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pay(Integer householdId, Integer duration, String handler) {
        List<PropertyFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .ne(PropertyFee::getIsPaid, 1)
                .orderByAsc(PropertyFee::getYear).orderByAsc(PropertyFee::getMonth));

        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        // 最近一条缴费记录（用于获取月费金额和确定预缴起始月）
        PropertyFee latest = feeMapper.selectOne(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .orderByDesc(PropertyFee::getYear).orderByDesc(PropertyFee::getMonth)
                .last("LIMIT 1"));

        // 获取月费金额
        BigDecimal monthlyAmount;
        if (!unpaid.isEmpty()) {
            monthlyAmount = unpaid.get(0).getAmount();
        } else if (latest != null) {
            monthlyAmount = latest.getAmount();
        } else {
            try {
                HouseholdDTO h = userFeignClient.getBriefById(householdId);
                monthlyAmount = h != null ? calcMonthlyAmount(h) : BigDecimal.ZERO;
            } catch (Exception e) {
                monthlyAmount = BigDecimal.ZERO;
            }
        }

        // 起缴年月
        int startYear, startMonth;

        if (unpaid.isEmpty()) {
            // 所有未缴已清：从最新记录的下个月开始预缴
            if (latest != null) {
                startMonth = latest.getMonth() + 1;
                startYear = latest.getYear();
                if (startMonth > 12) { startMonth = 1; startYear++; }
            } else {
                // 无任何记录：懒加载创建当月费用
                PropertyFee newFee = createCurrentMonthFee(householdId, monthlyAmount);
                feeMapper.insert(newFee);
                unpaid.add(newFee);
                startYear = curYear;
                startMonth = curMonth;
            }
        } else {
            PropertyFee first = unpaid.get(0);
            startYear = first.getYear();
            startMonth = first.getMonth();
        }

        // 计算缴费范围
        PaymentRange range = FeePaymentHelper.calculatePaymentRange(startYear, startMonth, duration, today);

        // 查询范围内所有已有记录（含已缴和未缴），避免预缴时 UNIQUE 冲突
        List<PropertyFee> allInRange = feeMapper.selectList(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId));

        // 筛选范围内已有的未缴记录
        List<PropertyFee> toPay = unpaid.stream()
                .filter(f -> FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range))
                .collect(Collectors.toList());

        // 范围内所有已有记录的月份（含已缴），防止重复创建
        Set<String> existing = allInRange.stream()
                .filter(f -> FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range))
                .map(f -> f.getYear() + "-" + f.getMonth()).collect(Collectors.toSet());

        // 范围内已存在的未缴记录：如果之前预缴过但未完全成功，也纳入本次缴费
        for (PropertyFee f : allInRange) {
            if (FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range)
                    && f.getIsPaid() != null && f.getIsPaid() != 1
                    && toPay.stream().noneMatch(p -> p.getFeeId().equals(f.getFeeId()))) {
                toPay.add(f);
            }
        }

        String billNo = FeePaymentHelper.generateBillNo("WY");

        // 预缴：范围内缺少的未来月份（>当前月），直接创建 is_paid=1 的记录
        for (int[] ym : FeePaymentHelper.computeMissingMonths(existing, range)) {
            if (ym[0] < curYear || (ym[0] == curYear && ym[1] <= curMonth)) continue;
            PropertyFee gf = new PropertyFee();
            gf.setHouseholdId(householdId); gf.setYear(ym[0]); gf.setMonth(ym[1]);
            gf.setAmount(monthlyAmount);
            gf.setIsPaid(1); gf.setPayDate(today); gf.setBillNo(billNo); gf.setHandler(handler);
            feeMapper.insert(gf);
            toPay.add(gf);
        }

        toPay.sort(Comparator.comparingInt((PropertyFee f) -> f.getYear()).thenComparingInt(f -> f.getMonth()));

        // 批量标记已有未缴记录为已缴
        BigDecimal total = BigDecimal.ZERO;
        for (PropertyFee f : toPay) {
            if (f.getIsPaid() == null || f.getIsPaid() != 1) {
                f.setIsPaid(1); f.setPayDate(today); f.setBillNo(billNo); f.setHandler(handler);
                feeMapper.updateById(f);
            }
            total = total.add(f.getAmount());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("billNo", billNo);
        result.put("monthCount", toPay.size());
        result.put("total", total);
        result.put("paidMonths", toPay.stream()
                .map(f -> f.getYear() + "年" + f.getMonth() + "月").collect(Collectors.toList()));
        return result;
    }

    // ==================== 入住时生成当月账单 ====================

    @Transactional(rollbackFor = Exception.class)
    public void generateBill(Integer householdId) {
        HouseholdDTO h = userFeignClient.getBriefById(householdId);
        if (h == null) throw new RuntimeException("住户不存在");

        LocalDate today = LocalDate.now();
        PropertyFee existing = feeMapper.selectOne(new LambdaQueryWrapper<PropertyFee>()
                .eq(PropertyFee::getHouseholdId, householdId)
                .eq(PropertyFee::getYear, today.getYear())
                .eq(PropertyFee::getMonth, today.getMonthValue()));
        if (existing != null) return;

        feeMapper.insert(createCurrentMonthFee(householdId, calcMonthlyAmount(h)));
    }

    // ==================== 私有 helper ====================

    private BigDecimal calcMonthlyAmount(HouseholdDTO h) {
        if (h.getArea() != null && h.getPropertyFeeRate() != null)
            return h.getArea().multiply(h.getPropertyFeeRate());
        return BigDecimal.ZERO;
    }

    private PropertyFee createCurrentMonthFee(Integer householdId, BigDecimal amount) {
        LocalDate today = LocalDate.now();
        PropertyFee fee = new PropertyFee();
        fee.setHouseholdId(householdId);
        fee.setYear(today.getYear()); fee.setMonth(today.getMonthValue());
        fee.setAmount(amount);
        fee.setIsPaid(today.getDayOfMonth() > deadlineService.getDeadlineDay() ? -1 : 0);
        return fee;
    }

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

    private List<int[]> buildMonthList(int year, int month, String period, int quarter) {
        List<int[]> list = new ArrayList<>();
        switch (period) {
            case "year":
                for (int m = 1; m <= 12; m++) list.add(new int[]{year, m});
                break;
            case "quarter":
                int startMonth = (quarter - 1) * 3 + 1;
                for (int m = startMonth; m < startMonth + 3; m++) list.add(new int[]{year, m});
                break;
            default:
                list.add(new int[]{year, month});
                break;
        }
        return list;
    }

    private Map<String, Object> aggregateHousehold(HouseholdDTO h, List<int[]> months,
                                                    LocalDate today, Map<String, PropertyFee> feeIndex) {
        BigDecimal totalAmount = BigDecimal.ZERO, collectedAmount = BigDecimal.ZERO;
        int worstStatus = 1, monthCount = 0;
        boolean anyActive = false;

        for (int[] ym : months) {
            int y = ym[0], m = ym[1];
            if (!isActiveInMonth(h, y, m)) continue;
            anyActive = true;
            if (y > today.getYear() || (y == today.getYear() && m > today.getMonthValue())) continue;
            monthCount++;

            String key = h.getHouseholdId() + "-" + y + "-" + m;
            PropertyFee fee = feeIndex.get(key);
            if (fee == null) continue;

            BigDecimal amt = fee.getAmount();
            int isPaid = fee.getIsPaid() != null ? fee.getIsPaid() : 0;
            totalAmount = totalAmount.add(amt);

            if (isPaid == 1) collectedAmount = collectedAmount.add(amt);
            else if (isPaid == -1) worstStatus = Math.max(worstStatus, 3);
            else worstStatus = Math.max(worstStatus, 2);
        }

        if (!anyActive) return null;

        String statusText;
        if (monthCount == 0) statusText = "无记录";
        else if (worstStatus == 1) statusText = "已缴";
        else if (worstStatus == 3 && collectedAmount.compareTo(BigDecimal.ZERO) > 0) statusText = "部分逾期";
        else if (worstStatus == 3) statusText = "逾期";
        else if (worstStatus == 2 && collectedAmount.compareTo(BigDecimal.ZERO) > 0) statusText = "部分待缴";
        else statusText = "待缴";

        Map<String, Object> d = new HashMap<>();
        d.put("householdId", h.getHouseholdId()); d.put("room", h.getRoom());
        d.put("ownerName", h.getOwnerName()); d.put("amount", totalAmount);
        d.put("collectedAmount", collectedAmount); d.put("statusText", statusText);
        d.put("monthCount", monthCount);
        return d;
    }
}
