package com.community.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.dto.HouseholdDTO;
import com.community.property.config.DeadlineConfig;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.entity.PropertyFee;
import com.community.property.feign.UserServiceFeignClient;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.mapper.ParkingSpaceMapper;
import com.community.property.mapper.PropertyFeeMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 物业费/停车费定时任务
 * <p>
 * - 每月1日凌晨2点：批量创建当月账单（物业费 + 停车费）
 * - 截止日次日凌晨2点：标记逾期
 * <p>
 * 注意：定时任务是"兜底保障"，不替代 controller 中已有的懒加载逻辑。
 * 懒加载仍是"实时响应"（如住户打开页面时即时创建当月记录）。
 */
@Component
public class FeeScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeeScheduler.class);

    @Resource
    private DeadlineConfig deadlineConfig;
    @Resource
    private JdbcTemplate jdbcTemplate;
    @Resource
    private PropertyFeeMapper propertyFeeMapper;
    @Resource
    private ParkingFeeMapper parkingFeeMapper;
    @Resource
    private ParkingSpaceMapper parkingSpaceMapper;
    @Resource
    private UserServiceFeignClient userFeignClient;

    // ==================== 每月1日：批量创建当月账单 ====================

    /**
     * 每月1日凌晨2:00执行，为所有在住住户和已租车位创建当月费用记录
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void createMonthlyBills() {
        log.info("【定时任务】开始批量创建当月账单...");
        LocalDate today = LocalDate.now();
        int year = today.getYear(), month = today.getMonthValue();

        // ---- 1. 物业费：所有在住住户 ----
        List<HouseholdDTO> households = null;
        try {
            households = userFeignClient.getActiveHouseholds();
        } catch (Exception e) {
            log.error("【定时任务】获取在住住户列表失败，物业费账单创建跳过", e);
            households = null;
        }
        int propCreated = 0;
        if (households != null) {
            for (HouseholdDTO h : households) {
                try {
                    PropertyFee existing = propertyFeeMapper.selectOne(
                            new LambdaQueryWrapper<PropertyFee>()
                                    .eq(PropertyFee::getHouseholdId, h.getHouseholdId())
                                    .eq(PropertyFee::getYear, year)
                                    .eq(PropertyFee::getMonth, month));
                    if (existing == null) {
                        BigDecimal amount = (h.getArea() != null && h.getPropertyFeeRate() != null)
                                ? h.getArea().multiply(h.getPropertyFeeRate()) : BigDecimal.ZERO;
                        PropertyFee fee = new PropertyFee();
                        fee.setHouseholdId(h.getHouseholdId());
                        fee.setYear(year);
                        fee.setMonth(month);
                        fee.setAmount(amount);
                        fee.setIsPaid(0);  // 待缴
                        propertyFeeMapper.insert(fee);
                        propCreated++;
                    }
                } catch (Exception e) {
                    log.warn("【定时任务】住户{}物业费账单创建失败: {}", h.getHouseholdId(), e.getMessage());
                }
            }
        }

        // ---- 2. 停车费：所有已租车位 ----
        List<ParkingSpace> activeSpaces = parkingSpaceMapper.selectList(
                new LambdaQueryWrapper<ParkingSpace>()
                        .eq(ParkingSpace::getStatus, 1)
                        .isNotNull(ParkingSpace::getHouseholdId));
        int parkCreated = 0;
        if (activeSpaces != null) {
            for (ParkingSpace sp : activeSpaces) {
                try {
                    ParkingFee existing = parkingFeeMapper.selectOne(
                            new LambdaQueryWrapper<ParkingFee>()
                                    .eq(ParkingFee::getSpaceNo, sp.getSpaceNo())
                                    .eq(ParkingFee::getYear, year)
                                    .eq(ParkingFee::getMonth, month));
                    if (existing == null) {
                        BigDecimal amount = sp.getMonthlyFee() != null ? sp.getMonthlyFee() : BigDecimal.ZERO;
                        ParkingFee fee = new ParkingFee();
                        fee.setSpaceNo(sp.getSpaceNo());
                        fee.setHouseholdId(sp.getHouseholdId());  // 冻结当前租户ID
                        fee.setYear(year);
                        fee.setMonth(month);
                        fee.setAmount(amount);
                        fee.setIsPaid(0);
                        parkingFeeMapper.insert(fee);
                        parkCreated++;
                    }
                } catch (Exception e) {
                    log.warn("【定时任务】车位{}停车费账单创建失败: {}", sp.getSpaceNo(), e.getMessage());
                }
            }
        }

        log.info("【定时任务】当月账单创建完成 — 物业费{}条, 停车费{}条", propCreated, parkCreated);
    }

    // ==================== 截止日次日：标记逾期 ====================

    /**
     * 每天凌晨2:00执行，检查今天是否为截止日次日，是则调用存储过程标记逾期
     * <p>
     * 为什么用每天 cron 而不是仅截止日次日？
     * 因为截止日配置在 DB 中可变（1~28），无法用固定 cron 表达式表达"截止日+1天"。
     * 方法内部有日期判断，非目标日直接返回，实际开销为零。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void markOverdueIfDeadlinePassed() {
        int deadlineDay = deadlineConfig.getDeadlineDay();
        int today = LocalDate.now().getDayOfMonth();

        // 计算逾期标记日：截止日 + 1，若超出当月天数则回绕到1号
        int overdueMarkDay = deadlineDay + 1;
        int maxDay = LocalDate.now().lengthOfMonth();
        if (overdueMarkDay > maxDay) {
            overdueMarkDay = 1;
        }

        // 非目标日，跳过
        if (today != overdueMarkDay) {
            return;
        }

        log.info("【定时任务】截止日{}号已过，开始标记逾期...", deadlineDay);
        try {
            jdbcTemplate.update("CALL sp_mark_overdue(?)", deadlineDay);
            log.info("【定时任务】逾期标记完成");
        } catch (Exception e) {
            log.error("【定时任务】标记逾期失败", e);
        }
    }
}
