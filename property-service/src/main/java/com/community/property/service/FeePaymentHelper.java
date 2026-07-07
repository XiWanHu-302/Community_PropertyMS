package com.community.property.service;

import java.time.LocalDate;
import java.util.*;

/**
 * 缴费公共逻辑 —— 消除 PropertyFeeController 和 ParkingFeeController 中的重复代码
 * 两个 pay() 方法的差异仅在实体类型和账单前缀，核心计算逻辑完全相同
 */
public class FeePaymentHelper {

    /**
     * 缴费范围（起止年月）
     */
    public static class PaymentRange {
        public final int startYear, startMonth, endYear, endMonth;

        public PaymentRange(int startYear, int startMonth, int endYear, int endMonth) {
            this.startYear = startYear;
            this.startMonth = startMonth;
            this.endYear = endYear;
            this.endMonth = endMonth;
        }
    }

    /**
     * 计算缴费起止范围
     *
     * @param firstYear   第一个未缴月的年份
     * @param firstMonth  第一个未缴月的月份
     * @param duration    缴费月数（1 / 6 / 12）
     * @param today       当前日期
     * @return 缴费范围：从第一个未缴月到截止月（至少覆盖当前月）
     */
    public static PaymentRange calculatePaymentRange(int firstYear, int firstMonth,
                                                      int duration, LocalDate today) {
        int curYear = today.getYear(), curMonth = today.getMonthValue();

        // 结束月 = 开始月 + 月数 - 1
        int endMonth = firstMonth + duration - 1;
        int endYear = firstYear;
        while (endMonth > 12) { endMonth -= 12; endYear++; }
        return new PaymentRange(firstYear, firstMonth, endYear, endMonth);
    }

    /**
     * 判断某年某月是否在缴费范围内
     */
    public static boolean inRange(int year, int month, PaymentRange r) {
        return !(year < r.startYear || (year == r.startYear && month < r.startMonth) ||
                 year > r.endYear || (year == r.endYear && month > r.endMonth));
    }

    /**
     * 计算范围内缺少的月份（已有记录集合 vs 完整范围）
     *
     * @param existingKeys 已有记录的 "年-月" 字符串集合，如 {"2026-1", "2026-3"}
     * @param range        缴费范围
     * @return 缺少的月份列表，每个元素为 [year, month]
     */
    public static List<int[]> computeMissingMonths(Set<String> existingKeys, PaymentRange range) {
        List<int[]> missing = new ArrayList<>();
        int y = range.startYear, m = range.startMonth;
        while (y < range.endYear || (y == range.endYear && m <= range.endMonth)) {
            String key = y + "-" + m;
            if (!existingKeys.contains(key)) {
                missing.add(new int[]{y, m});
            }
            m++;
            if (m > 12) { m = 1; y++; }
        }
        return missing;
    }

    /**
     * 生成缴费单号
     *
     * @param prefix 前缀：物业费 "WY"，停车费 "TC"
     * @return 如 "WY20260705165930"
     */
    public static String generateBillNo(String prefix) {
        return prefix + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
    }

    // ==================== 状态判定（物业费和停车费共用） ====================

    /**
     * is_paid 三态 → 展示文本（物业费和停车费共用核心逻辑）
     * <p>
     * -1 = 逾期, 0 = 待缴, 1 = 已缴（未来月份已缴显示为"提前缴费"）
     * <p>
     * 此方法只做纯状态映射，不涉及"历史记录"等业务特有概念（由各 Controller 自行叠加）
     *
     * @param isPaid     -1逾期 / 0待缴 / 1已缴
     * @param year       账单年份
     * @param month      账单月份
     * @param today      当前日期
     * @param deadlineDay 每月缴费截止日（1-28）
     * @return "已缴" / "提前缴费" / "逾期" / "待缴"
     */
    public static String feeStatusText(Integer isPaid, int year, int month,
                                        LocalDate today, int deadlineDay) {
        if (isPaid == null) return "待缴";
        if (isPaid == 1) {
            // 未来月份已缴 → 提前缴费
            if (year > today.getYear() || (year == today.getYear() && month > today.getMonthValue()))
                return "提前缴费";
            return "已缴";
        }
        if (isPaid == -1) return "逾期";
        // is_paid = 0：历史月份为逾期；当前月根据截止日判定
        if (year < today.getYear() || (year == today.getYear() && month < today.getMonthValue()))
            return "逾期";
        if (year == today.getYear() && month == today.getMonthValue()
                && today.getDayOfMonth() > deadlineDay)
            return "逾期";
        return "待缴";
    }
}
