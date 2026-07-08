package com.community.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.dto.HouseholdDTO;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.feign.UserServiceFeignClient;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.mapper.ParkingSpaceMapper;
import com.community.property.service.FeePaymentHelper.PaymentRange;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 停车费业务逻辑
 * <p>
 * 原则：数据库是唯一真相来源。不补建缺失月份，不调用全局逾期标记。
 */
@Service
public class ParkingFeeService {

    @Resource private ParkingFeeMapper feeMapper;
    @Resource private ParkingSpaceMapper spaceMapper;
    @Resource private UserServiceFeignClient userFeignClient;
    @Resource private FeeDeadlineService deadlineService;

    // ==================== 汇总统计 ====================

    public Map<String, Object> summary(Integer year, Integer month, String period,
                                        Integer quarter, String statusFilter) {
        LocalDate today = LocalDate.now();
        List<ParkingSpace> spaces = spaceMapper.selectList(null);
        List<int[]> monthsToAggregate = buildMonthList(year, month, period, quarter);

        Map<String, ParkingFee> feeIndex = new HashMap<>();
        for (int[] ym : monthsToAggregate) {
            List<ParkingFee> monthFees = feeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                    .eq(ParkingFee::getYear, ym[0]).eq(ParkingFee::getMonth, ym[1]));
            for (ParkingFee f : monthFees) {
                feeIndex.put(f.getSpaceNo() + "-" + f.getYear() + "-" + f.getMonth(), f);
            }
        }

        BigDecimal totalReceivable = BigDecimal.ZERO, totalCollected = BigDecimal.ZERO, totalOutstanding = BigDecimal.ZERO;
        int paidCount = 0, pendingCount = 0, overdueCount = 0;
        List<Map<String, Object>> details = new ArrayList<>();

        for (ParkingSpace s : spaces) {
            Map<String, Object> d = aggregateSpace(s, monthsToAggregate, today, feeIndex);
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
                    else if (status.contains("待缴") || status.contains("历史")) pendingCount++;
                    else paidCount++;
                    break;
            }
            details.add(d);
        }

        if (statusFilter != null && !statusFilter.isEmpty() && !"全部".equals(statusFilter)) {
            details = details.stream().filter(d -> statusFilter.equals(d.get("statusText"))).collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("year", year); result.put("period", period);
        if ("month".equals(period)) result.put("month", month);
        if ("quarter".equals(period)) result.put("quarter", quarter);
        result.put("monthCount", monthsToAggregate.size());
        result.put("totalSpaces", details.size());
        result.put("totalReceivable", totalReceivable);
        result.put("totalCollected", totalCollected);
        result.put("totalOutstanding", totalOutstanding);
        result.put("paidCount", paidCount); result.put("pendingCount", pendingCount);
        result.put("overdueCount", overdueCount); result.put("details", details);
        return result;
    }

    // ==================== 查询某车位停车费 ====================

    public List<Map<String, Object>> listFees(String spaceNo, Integer year) {
        ParkingSpace s = spaceMapper.selectById(spaceNo);
        LocalDate assignedDate = s != null ? s.getAssignedDate() : null;
        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        LambdaQueryWrapper<ParkingFee> w = new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getSpaceNo, spaceNo)
                .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth);
        if (year != null) w.eq(ParkingFee::getYear, year);
        List<ParkingFee> fees = feeMapper.selectList(w);

        List<Map<String, Object>> vos = new ArrayList<>();
        for (ParkingFee f : fees) {
            if (f.getYear() > curYear || (f.getYear() == curYear && f.getMonth() > curMonth)) {
                if (f.getIsPaid() == null || f.getIsPaid() != 1) continue;
            }
            Map<String, Object> vo = new HashMap<>();
            vo.put("feeId", f.getFeeId()); vo.put("spaceNo", f.getSpaceNo());
            vo.put("year", f.getYear()); vo.put("month", f.getMonth());
            vo.put("amount", f.getAmount()); vo.put("isPaid", f.getIsPaid());
            vo.put("payDate", f.getPayDate()); vo.put("billNo", f.getBillNo());
            vo.put("statusText", statusTextForParking(f, f.getYear(), f.getMonth(), assignedDate));
            vos.add(vo);
        }
        return vos;
    }

    // ==================== 缴费 ====================

    /**
     * 缴费：
     * 1. 现有未缴记录 → 标记已缴
     * 2. 所有未缴已清时，从下个月开始纯预缴
     * 3. 预缴时创建未来月份记录直接标记 is_paid=1
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> pay(String spaceNo, Integer duration, String handler) {
        List<ParkingFee> unpaid = feeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getSpaceNo, spaceNo).ne(ParkingFee::getIsPaid, 1)
                .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));

        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        ParkingSpace sp = spaceMapper.selectById(spaceNo);
        if (sp == null) throw new RuntimeException("车位不存在");
        BigDecimal monthlyAmount = sp.getMonthlyFee() != null ? sp.getMonthlyFee() : BigDecimal.ZERO;

        // 最近一条缴费记录（用于确定预缴起始月）
        ParkingFee latest = feeMapper.selectOne(new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getSpaceNo, spaceNo)
                .orderByDesc(ParkingFee::getYear).orderByDesc(ParkingFee::getMonth)
                .last("LIMIT 1"));

        int startYear, startMonth;

        if (unpaid.isEmpty()) {
            if (sp.getHouseholdId() == null) throw new RuntimeException("该车位尚未分配给住户，无法缴费");
            // 所有未缴已清：从最新记录的下个月开始预缴
            if (latest != null) {
                startMonth = latest.getMonth() + 1;
                startYear = latest.getYear();
                if (startMonth > 12) { startMonth = 1; startYear++; }
            } else {
                // 无任何记录：懒加载创建当月费用
                ParkingFee newFee = createCurrentMonthFee(spaceNo, sp.getHouseholdId(), monthlyAmount);
                feeMapper.insert(newFee);
                unpaid.add(newFee);
                startYear = curYear;
                startMonth = curMonth;
            }
        } else {
            ParkingFee first = unpaid.get(0);
            startYear = first.getYear();
            startMonth = first.getMonth();
        }

        PaymentRange range = FeePaymentHelper.calculatePaymentRange(startYear, startMonth, duration, today);

        // 查询范围内所有已有记录（含已缴和未缴），避免预缴时 UNIQUE 冲突
        List<ParkingFee> allInRange = feeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .eq(ParkingFee::getSpaceNo, spaceNo));

        List<ParkingFee> toPay = unpaid.stream()
                .filter(f -> FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range))
                .collect(Collectors.toList());

        // 范围内所有已有记录的月份（含已缴），防止重复创建
        Set<String> existing = allInRange.stream()
                .filter(f -> FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range))
                .map(f -> f.getYear() + "-" + f.getMonth()).collect(Collectors.toSet());

        // 范围内已存在的未缴记录：如果之前预缴过但未完全成功，也纳入本次缴费
        for (ParkingFee f : allInRange) {
            if (FeePaymentHelper.inRange(f.getYear(), f.getMonth(), range)
                    && f.getIsPaid() != null && f.getIsPaid() != 1
                    && toPay.stream().noneMatch(p -> p.getFeeId().equals(f.getFeeId()))) {
                toPay.add(f);
            }
        }

        String billNo = FeePaymentHelper.generateBillNo("TC");

        for (int[] ym : FeePaymentHelper.computeMissingMonths(existing, range)) {
            if (ym[0] < curYear || (ym[0] == curYear && ym[1] <= curMonth)) continue;
            ParkingFee gf = new ParkingFee();
            gf.setSpaceNo(spaceNo); gf.setYear(ym[0]); gf.setMonth(ym[1]);
            gf.setHouseholdId(sp.getHouseholdId());
            gf.setAmount(monthlyAmount);
            gf.setIsPaid(1); gf.setPayDate(today); gf.setBillNo(billNo); gf.setHandler(handler);
            feeMapper.insert(gf);
            toPay.add(gf);
        }

        toPay.sort(Comparator.comparingInt((ParkingFee f) -> f.getYear()).thenComparingInt(f -> f.getMonth()));

        BigDecimal total = BigDecimal.ZERO;
        for (ParkingFee f : toPay) {
            if (f.getIsPaid() == null || f.getIsPaid() != 1) {
                f.setIsPaid(1); f.setPayDate(today); f.setBillNo(billNo); f.setHandler(handler);
                feeMapper.updateById(f);
            }
            total = total.add(f.getAmount());
        }

        Map<String, Object> r = new HashMap<>();
        r.put("billNo", billNo); r.put("monthCount", toPay.size()); r.put("total", total);
        return r;
    }

    // ==================== 私有 helper ====================

    private String statusTextForParking(ParkingFee fee, int year, int month, LocalDate assignedDate) {
        if (assignedDate != null
                && (year < assignedDate.getYear()
                    || (year == assignedDate.getYear() && month < assignedDate.getMonthValue())))
            return "历史记录";
        return FeePaymentHelper.feeStatusText(fee != null ? fee.getIsPaid() : null);
    }

    private ParkingFee createCurrentMonthFee(String spaceNo, Integer householdId, BigDecimal monthlyFee) {
        LocalDate today = LocalDate.now();
        ParkingFee fee = new ParkingFee();
        fee.setSpaceNo(spaceNo); fee.setHouseholdId(householdId);
        fee.setYear(today.getYear()); fee.setMonth(today.getMonthValue());
        fee.setAmount(monthlyFee != null ? monthlyFee : BigDecimal.ZERO);
        fee.setIsPaid(today.getDayOfMonth() > deadlineService.getDeadlineDay() ? -1 : 0);
        return fee;
    }

    private boolean isAssignedInMonth(ParkingSpace s, int year, int month) {
        if (s.getAssignedDate() == null) return s.getHouseholdId() != null;
        if (year < s.getAssignedDate().getYear()) return false;
        if (year == s.getAssignedDate().getYear() && month < s.getAssignedDate().getMonthValue()) return false;
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

    private Map<String, Object> aggregateSpace(ParkingSpace s, List<int[]> months,
                                                LocalDate today, Map<String, ParkingFee> feeIndex) {
        BigDecimal totalAmount = BigDecimal.ZERO, collectedAmount = BigDecimal.ZERO;
        int worstStatus = 1, monthCount = 0;
        boolean anyRecord = false;
        boolean singleMonth = (months.size() == 1);
        String payDate = null, handler = null, billNo = null;

        for (int[] ym : months) {
            int y = ym[0], m = ym[1];
            if (!isAssignedInMonth(s, y, m)) continue;
            if (y > today.getYear() || (y == today.getYear() && m > today.getMonthValue())) continue;
            monthCount++;

            String key = s.getSpaceNo() + "-" + y + "-" + m;
            ParkingFee fee = feeIndex.get(key);
            if (fee == null) continue;

            BigDecimal amt = fee.getAmount();
            anyRecord = true;
            if (singleMonth && fee.getIsPaid() != null && fee.getIsPaid() == 1) {
                payDate = fee.getPayDate() != null ? fee.getPayDate().toString() : "";
                handler = fee.getHandler() != null ? fee.getHandler() : "";
                billNo = fee.getBillNo() != null ? fee.getBillNo() : "";
            }
            totalAmount = totalAmount.add(amt);

            String status = statusTextForParking(fee, y, m, s.getAssignedDate());
            if (status.equals("已缴") || status.equals("提前缴费")) collectedAmount = collectedAmount.add(amt);
            else if (status.equals("逾期")) worstStatus = Math.max(worstStatus, 3);
            else worstStatus = Math.max(worstStatus, 2);
        }

        if (monthCount == 0 && !anyRecord) return null;

        String statusText;
        if (monthCount == 0) statusText = "无记录";
        else if (worstStatus == 1) statusText = "已缴";
        else if (worstStatus == 3 && collectedAmount.compareTo(BigDecimal.ZERO) > 0) statusText = "部分逾期";
        else if (worstStatus == 3) statusText = "逾期";
        else if (worstStatus == 2 && collectedAmount.compareTo(BigDecimal.ZERO) > 0) statusText = "部分待缴";
        else statusText = "待缴";

        String ownerName = "", room = "";
        Integer lookupId = s.getHouseholdId();
        if (lookupId != null) {
            HouseholdDTO h = userFeignClient.getBriefById(lookupId);
            if (h != null) { ownerName = h.getOwnerName(); room = h.getRoom(); }
        }

        Map<String, Object> d = new HashMap<>();
        d.put("spaceNo", s.getSpaceNo()); d.put("plateNo", s.getPlateNo() != null ? s.getPlateNo() : "");
        d.put("ownerName", ownerName); d.put("room", room);
        d.put("amount", totalAmount); d.put("collectedAmount", collectedAmount);
        d.put("statusText", statusText); d.put("monthCount", monthCount);
        d.put("payDate", payDate); d.put("handler", handler != null ? handler : "");
        d.put("billNo", billNo != null ? billNo : "");
        return d;
    }
}
