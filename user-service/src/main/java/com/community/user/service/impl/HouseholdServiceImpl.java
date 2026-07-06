package com.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.community.user.dto.HouseholdQueryDTO;
import com.community.user.entity.Building;
import com.community.user.entity.Household;
import com.community.user.entity.User;
import com.community.user.mapper.BuildingMapper;
import com.community.user.mapper.HouseholdMapper;
import com.community.user.feign.PropertyServiceFeignClient;
import com.community.user.mapper.UserMapper;
import com.community.user.service.HouseholdService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 住户服务实现
 */
@Service
public class HouseholdServiceImpl
        extends ServiceImpl<HouseholdMapper, Household>
        implements HouseholdService {

    @Resource
    private BuildingMapper buildingMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private PropertyServiceFeignClient propertyFeignClient;

    // ==================== 多条件组合查询 ====================

    @Override
    public List<Household> queryByConditions(HouseholdQueryDTO query) {
        LambdaQueryWrapper<Household> wrapper = new LambdaQueryWrapper<>();

        if (StringUtils.hasText(query.getBuildingNo())) {
            wrapper.eq(Household::getBuildingNo, query.getBuildingNo());
        }
        if (query.getFloorNo() != null) {
            wrapper.eq(Household::getFloorNo, query.getFloorNo());
        }
        if (query.getUnitNo() != null) {
            wrapper.eq(Household::getUnitNo, query.getUnitNo());
        }
        if (StringUtils.hasText(query.getOwnerName())) {
            wrapper.like(Household::getOwnerName, query.getOwnerName());
        }
        if (StringUtils.hasText(query.getPhone())) {
            wrapper.like(Household::getPhone, query.getPhone());
        }
        if (query.getStatus() != null) {
            wrapper.eq(Household::getStatus, query.getStatus());
        }

        wrapper.orderByDesc(Household::getCheckInDate);
        return this.list(wrapper);
    }

    // ==================== 房号合法性校验 ====================

    private void validateRoom(Building building, int floorNo, int unitNo, Integer excludeHouseholdId) {
        if (floorNo > building.getFloorCount()) {
            throw new RuntimeException(String.format(
                    "%s 楼只有 %d 层，层号 %d 超出范围", building.getBuildingNo(), building.getFloorCount(), floorNo));
        }
        if (unitNo > building.getUnitsPerFloor()) {
            throw new RuntimeException(String.format(
                    "%s 楼每层只有 %d 户，户号 %d 超出范围", building.getBuildingNo(), building.getUnitsPerFloor(), unitNo));
        }
        // 只检查在住状态的住户是否重复（已搬离的允许同房间新登记）
        LambdaQueryWrapper<Household> wrapper = new LambdaQueryWrapper<Household>()
                .eq(Household::getBuildingNo, building.getBuildingNo())
                .eq(Household::getFloorNo, floorNo)
                .eq(Household::getUnitNo, unitNo)
                .eq(Household::getStatus, 1);   // 只查在住的
        if (excludeHouseholdId != null) {
            wrapper.ne(Household::getHouseholdId, excludeHouseholdId);
        }
        Long count = this.count(wrapper);
        if (count > 0) {
            throw new RuntimeException(String.format(
                    "%s 楼 %d 层 %02d 户号当前已有住户在住，不能重复登记",
                    building.getBuildingNo(), floorNo, unitNo));
        }
    }

    // ==================== 生成业主默认用户名 ====================
    // 格式：yz-281302-202607041759

    private String generateResidentUsername(String buildingNo, int floorNo, int unitNo) {
        String room = buildingNo + String.format("%02d", floorNo) + String.format("%02d", unitNo);
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        return "yz-" + room + "-" + time;
    }

    // ==================== 新增住户 + 自动创建登录账号 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User addHousehold(Household household) {
        // 1. 校验楼号
        Building building = buildingMapper.selectById(household.getBuildingNo());
        if (building == null) {
            throw new RuntimeException("楼号 " + household.getBuildingNo() + " 不存在");
        }

        // 2. 校验房间（只查在住的）
        validateRoom(building, household.getFloorNo(), household.getUnitNo(), null);

        // 3. 设置默认值
        if (household.getStatus() == null) household.setStatus(1);
        if (household.getCheckInDate() == null) household.setCheckInDate(LocalDate.now());
        if (household.getRepairFundBalance() == null) household.setRepairFundBalance(java.math.BigDecimal.ZERO);
        if (household.getFamilySize() == null) household.setFamilySize(1);

        // 4. 保存住户
        this.save(household);

        // 5. 自动生成业主登录账号
        User user = new User();
        user.setUsername(generateResidentUsername(household.getBuildingNo(), household.getFloorNo(), household.getUnitNo()));
        user.setPassword(passwordEncoder.encode("123456"));
        user.setRole("resident");
        user.setRefId(String.valueOf(household.getHouseholdId()));
        user.setStatus(1);

        // 极小概率用户名冲突，追加序号
        Long count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, user.getUsername()));
        int seq = 1;
        while (count > 0) {
            user.setUsername(user.getUsername() + "-" + seq);
            count = userMapper.selectCount(new LambdaQueryWrapper<User>()
                    .eq(User::getUsername, user.getUsername()));
            seq++;
        }

        userMapper.insert(user);

        // 清空密码再返回
        user.setPassword("123456");
        return user;
    }

    // ==================== 修改住户（含校验） ====================

    @Override
    public boolean updateHouseholdWithValidate(Household household) {
        Building building = buildingMapper.selectById(household.getBuildingNo());
        if (building == null) {
            throw new RuntimeException("楼号 " + household.getBuildingNo() + " 不存在");
        }
        validateRoom(building, household.getFloorNo(), household.getUnitNo(), household.getHouseholdId());
        return this.updateById(household);
    }

    // ==================== 搬离 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean moveOut(Integer householdId) {
        Household household = this.getById(householdId);
        if (household == null) throw new RuntimeException("住户不存在");
        if (household.getStatus() == 0) throw new RuntimeException("该住户已是搬离状态");

        // 检查是否有未缴费用（通过 Feign 调用 property-service）
        Map<String, Object> unpaid;
        try {
            unpaid = propertyFeignClient.getUnpaidFees(householdId);
        } catch (Exception e) {
            throw new RuntimeException("无法查询未缴费用（property-service 可能未启动），请稍后重试");
        }
        if (unpaid != null && unpaid.get("totalUnpaid") != null) {
            java.math.BigDecimal total = new java.math.BigDecimal(unpaid.get("totalUnpaid").toString());
            if (total.compareTo(java.math.BigDecimal.ZERO) > 0) {
                throw new RuntimeException(String.format(
                    "该住户尚有 ¥%s 未缴费用（物业费+停车费），请先缴清后再搬离", total));
            }
        }

        household.setStatus(0);
        household.setCheckOutDate(LocalDate.now());
        this.updateById(household);

        // 同时禁用关联的业主登录账号
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getRefId, String.valueOf(householdId))
                .eq(User::getRole, "resident"));
        if (user != null) {
            user.setStatus(0);
            userMapper.updateById(user);
        }
        return true;
    }
}
