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
 * 停车费查询服务 —— 只查数据库真实数据，不补建缺失月份
 */
@Component
public class ParkingFeeQueryService {

    @Resource private ParkingFeeMapper parkingFeeMapper;
    @Resource private ParkingSpaceMapper spaceMapper;

    /**
     * 查询某住户名下所有车位的未缴停车费（仅数据库已有记录）
     *
     * @return map 包含: unpaidList, totalUnpaid
     */
    public Map<String, Object> getParkingUnpaidForHousehold(Integer householdId) {
        // 1. 查住户名下所有车位
        List<ParkingSpace> mySpaces = spaceMapper.selectList(new LambdaQueryWrapper<ParkingSpace>()
                .eq(ParkingSpace::getHouseholdId, householdId));

        List<ParkingFee> parkUnpaid = new ArrayList<>();
        if (mySpaces.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("unpaidList", parkUnpaid);
            result.put("totalUnpaid", BigDecimal.ZERO);
            return result;
        }

        // 2. 收集所有 spaceNo
        List<String> spaceNos = mySpaces.stream()
                .map(ParkingSpace::getSpaceNo)
                .collect(Collectors.toList());

        // 3. 一次 batch 查询所有未缴停车费（is_paid != 1）
        parkUnpaid = parkingFeeMapper.selectList(new LambdaQueryWrapper<ParkingFee>()
                .in(ParkingFee::getSpaceNo, spaceNos)
                .ne(ParkingFee::getIsPaid, 1)
                .orderByAsc(ParkingFee::getYear).orderByAsc(ParkingFee::getMonth));

        BigDecimal totalUnpaid = parkUnpaid.stream()
                .map(ParkingFee::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("unpaidList", parkUnpaid);
        result.put("totalUnpaid", totalUnpaid);
        return result;
    }
}
