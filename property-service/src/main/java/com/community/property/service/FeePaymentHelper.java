package com.community.property.service;

import java.time.LocalDate;
import java.util.*;

/**
 * 缴费公共逻辑 —— 物业费和停车费共用的纯计算工具
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
     * @param firstYear  第一个未缴月的年份
     * @param firstMonth 第一个未缴月的月份
     * @param duration   缴费月数（1 / 6 / 12）
     * @param today      当前日期
     * @return 缴费范围：从第一个未缴月到截止月
     */
    public static PaymentRange calculatePaymentRange(int firstYear, int firstMonth,
                                                      int duration, LocalDate today) {
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
     * 计算范围内缺少的月份
     *
     * @param existingKeys 已有记录的 "年-月" 字符串集合
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
     * 生成缴费单号，格式：前缀 + yyyyMMddHHmmss + 3位随机数
     *
     * @param prefix 前缀：物业费 "WY"，停车费 "TC"
     * @return 如 "WY20260703150230001"
     */
    public static String generateBillNo(String prefix) {
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String random = String.format("%03d", new Random().nextInt(1000));
        return prefix + timestamp + random;
    }

    // ==================== 状态判定（物业费和停车费共用） ====================

    /**
     * is_paid 三态 → 展示文本
     * <p>
     * -1 = 逾期, 0 = 待缴, 1 = 已缴
     * <p>
     * 数据库 is_paid 字段是唯一真相来源，不做日期推导
     *
     * @param isPaid -1逾期 / 0待缴 / 1已缴
     * @return "已缴" / "逾期" / "待缴"
     */
    public static String feeStatusText(Integer isPaid) {
        if (isPaid == null) return "待缴";
        switch (isPaid) {
            case -1: return "逾期";
            case 0:  return "待缴";
            case 1:  return "已缴";
            default: return "待缴";
        }
    }
}
