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
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 物业费/停车费定时任务 —— 每月1日批量创建当月账单
 * <p>
 * 逾期标记由数据库端（MySQL 定时事件调用 sp_mark_overdue）处理，应用层不参与。
 */
@Component
public class FeeScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeeScheduler.class);

    @Resource private PropertyFeeMapper propertyFeeMapper;
    @Resource private ParkingFeeMapper parkingFeeMapper;
    @Resource private ParkingSpaceMapper parkingSpaceMapper;
    @Resource private UserServiceFeignClient userFeignClient;

    /**
     * 每月1日凌晨2:00执行，为所有在住住户和已租车位创建当月费用记录
     */
    @Scheduled(cron = "0 0 2 1 * ?")
    public void createMonthlyBills() {
        log.info("【定时任务】开始批量创建当月账单...");
        LocalDate today = LocalDate.now();
        int year = today.getYear(), month = today.getMonthValue();

        // ---- 1. 物业费：批量查询已有记录，内存去重（修复 N+1） ----
        List<HouseholdDTO> households = null;
        try {
            households = userFeignClient.getActiveHouseholds();
        } catch (Exception e) {
            log.error("【定时任务】获取在住住户列表失败，物业费账单创建跳过", e);
            households = null;
        }
        int propCreated = 0;
        if (households != null && !households.isEmpty()) {
            // 批量查询当月已有记录
            List<PropertyFee> existingFees = propertyFeeMapper.selectList(
                    new LambdaQueryWrapper<PropertyFee>()
                            .eq(PropertyFee::getYear, year)
                            .eq(PropertyFee::getMonth, month));
            Set<Integer> existingIds = existingFees.stream()
                    .map(PropertyFee::getHouseholdId).collect(Collectors.toSet());

            for (HouseholdDTO h : households) {
                try {
                    if (!existingIds.contains(h.getHouseholdId())) {
                        BigDecimal amount = (h.getArea() != null && h.getPropertyFeeRate() != null)
                                ? h.getArea().multiply(h.getPropertyFeeRate()) : BigDecimal.ZERO;
                        PropertyFee fee = new PropertyFee();
                        fee.setHouseholdId(h.getHouseholdId());
                        fee.setYear(year); fee.setMonth(month);
                        fee.setAmount(amount);
                        fee.setIsPaid(0);
                        propertyFeeMapper.insert(fee);
                        propCreated++;
                    }
                } catch (Exception e) {
                    log.warn("【定时任务】住户{}物业费账单创建失败: {}", h.getHouseholdId(), e.getMessage());
                }
            }
        }

        // ---- 2. 停车费：批量查询已有记录，内存去重（修复 N+1） ----
        List<ParkingSpace> activeSpaces = parkingSpaceMapper.selectList(
                new LambdaQueryWrapper<ParkingSpace>()
                        .eq(ParkingSpace::getStatus, 1)
                        .isNotNull(ParkingSpace::getHouseholdId));
        int parkCreated = 0;
        if (activeSpaces != null && !activeSpaces.isEmpty()) {
            List<ParkingFee> existingParkingFees = parkingFeeMapper.selectList(
                    new LambdaQueryWrapper<ParkingFee>()
                            .eq(ParkingFee::getYear, year)
                            .eq(ParkingFee::getMonth, month));
            Set<String> existingSpaceNos = existingParkingFees.stream()
                    .map(ParkingFee::getSpaceNo).collect(Collectors.toSet());

            for (ParkingSpace sp : activeSpaces) {
                try {
                    if (!existingSpaceNos.contains(sp.getSpaceNo())) {
                        BigDecimal amount = sp.getMonthlyFee() != null ? sp.getMonthlyFee() : BigDecimal.ZERO;
                        ParkingFee fee = new ParkingFee();
                        fee.setSpaceNo(sp.getSpaceNo());
                        fee.setHouseholdId(sp.getHouseholdId());
                        fee.setYear(year); fee.setMonth(month);
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
}
