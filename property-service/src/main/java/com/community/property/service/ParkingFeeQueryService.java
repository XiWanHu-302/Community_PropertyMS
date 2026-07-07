package com.community.property.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.mapper.ParkingSpaceMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 停车费查询服务 —— 封装停车费相关的数据库查询，供 PropertyFeeController 等调用
 * <p>
 * 解决两个问题：
 * <ul>
 *   <li>避免 Controller 直接注入 ParkingFeeMapper/ParkingSpaceMapper（职责边界）</li>
 *   <li>消除 N+1 查询：一次 batch 查询代替逐车位循环查询</li>
 * </ul>
 */
@Component
public class ParkingFeeQueryService {

    @Resource
    private ParkingFeeMapper parkingFeeMapper;

    @Resource
    private ParkingSpaceMapper spaceMapper;

    /**
     * 查询某住户名下所有车位的未缴停车费及缺失月份
     *
     * @param householdId 住户ID
     * @param today       当前日期
     * @return map 包含: unpaidList, missingList, totalUnpaid, totalMissing
     */
    public Map<String, Object> getParkingUnpaidForHousehold(Integer householdId,
                                                             LocalDate today) {
        // 1. 一次性查询该住户名下所有车位的全部停车费记录（避免 N+1）
        List<ParkingSpace> mySpaces = spaceMapper.selectList(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getHouseholdId, householdId));

        List<ParkingFee> parkUnpaid = new ArrayList<>();
        List<Map<String, Object>> parkMissing = new ArrayList<>();

        if (mySpaces.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("unpaidList", parkUnpaid);
            result.put("missingList", parkMissing);
            result.put("totalUnpaid", BigDecimal.ZERO);
            result.put("totalMissing", BigDecimal.ZERO);
            return result;
        }

        // 收集所有 spaceNo
        List<String> spaceNos = mySpaces.stream()
                .map(ParkingSpace::getSpaceNo)
                .collect(Collectors.toList());

        // 2. 未缴停车费（一次 batch 查询）
        parkUnpaid = parkingFeeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .in(ParkingFee::getSpaceNo, spaceNos)
                .ne(ParkingFee::getIsPaid, 1)
                .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));

        // 3. 全部记录（一次 batch 查询）— 按 spaceNo 分组
        List<ParkingFee> allFees = parkingFeeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .in(ParkingFee::getSpaceNo, spaceNos));
        Map<String, Set<String>> existingBySpace = new HashMap<>();
        for (ParkingFee f : allFees) {
            existingBySpace.computeIfAbsent(f.getSpaceNo(), k -> new HashSet<>())
                    .add(f.getYear() + "-" + f.getMonth());
        }

        // 4. 逐车位计算缺失月份
        for (ParkingSpace sp : mySpaces) {
            Set<String> spaceExistingMonths = existingBySpace.getOrDefault(sp.getSpaceNo(), Collections.emptySet());

            // 从车位分配日开始计费，无分配日期则从当年1月开始（旧数据兼容）
            int startY = today.getYear(), startM = 1;
            if (sp.getAssignedDate() != null) {
                startY = sp.getAssignedDate().getYear();
                startM = sp.getAssignedDate().getMonthValue();
            }

            int y = startY, m = startM;
            while (y < today.getYear() || (y == today.getYear() && m <= today.getMonthValue())) {
                String key = y + "-" + m;
                if (!spaceExistingMonths.contains(key)) {
                    Map<String, Object> d = new HashMap<>();
                    d.put("type", "停车费");
                    d.put("spaceNo", sp.getSpaceNo());
                    d.put("year", y);
                    d.put("month", m);
                    d.put("amount", sp.getMonthlyFee() != null ? sp.getMonthlyFee() : BigDecimal.ZERO);
                    parkMissing.add(d);
                }
                m++;
                if (m > 12) { m = 1; y++; }
            }
        }

        BigDecimal totalUnpaid = parkUnpaid.stream()
                .map(ParkingFee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMissing = parkMissing.stream()
                .map(d -> (BigDecimal) d.get("amount"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("unpaidList", parkUnpaid);
        result.put("missingList", parkMissing);
        result.put("totalUnpaid", totalUnpaid);
        result.put("totalMissing", totalMissing);
        return result;
    }
}
