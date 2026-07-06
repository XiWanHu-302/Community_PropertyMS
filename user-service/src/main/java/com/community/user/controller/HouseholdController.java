package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.common.dto.HouseholdDTO;
import com.community.user.dto.HouseholdQueryDTO;
import com.community.user.entity.Household;
import com.community.user.entity.User;
import com.community.user.mapper.UserMapper;
import com.community.user.feign.PropertyServiceFeignClient;
import com.community.user.service.HouseholdService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 住户管理控制器 —— 管理员操作
 */
@RestController
@RequestMapping("/household")
public class HouseholdController {

    private static final Logger log = LoggerFactory.getLogger(HouseholdController.class);

    @Resource
    private HouseholdService householdService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private PropertyServiceFeignClient propertyFeignClient;

    /**
     * 多条件组合查询
     * POST /household/list
     */
    @PostMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    public Result<List<Household>> list(@RequestBody HouseholdQueryDTO query) {
        List<Household> list = householdService.queryByConditions(query);
        return Result.ok(list);
    }

    /**
     * 按ID查询住户详情
     * GET /household/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT','MAINTENANCE')")
    public Result<Household> getById(@PathVariable Integer id) {
        Household household = householdService.getById(id);
        if (household == null) return Result.fail("住户不存在");
        return Result.ok(household);
    }

    // ==================== 内部接口（供 property-service 通过 OpenFeign 调用） ====================

    /** 查询所有在住住户（供 property-service 内部调用） */
    @GetMapping("/active")
    public Result<List<HouseholdDTO>> activeList() {
        List<Household> list = householdService.queryByConditions(new HouseholdQueryDTO());
        // 只返回在住的
        list = list.stream().filter(h -> h.getStatus() == 1).collect(Collectors.toList());
        return Result.ok(list.stream().map(this::toDTO).collect(Collectors.toList()));
    }

    /** 按住号搜索住户（供 property-service 内部分配车位时使用） */
    @GetMapping("/search-by-room")
    public Result<HouseholdDTO> searchByRoom(@RequestParam String buildingNo,
                                              @RequestParam Integer floorNo,
                                              @RequestParam Integer unitNo) {
        HouseholdQueryDTO q = new HouseholdQueryDTO();
        q.setBuildingNo(buildingNo);
        q.setFloorNo(floorNo);
        q.setUnitNo(unitNo);
        q.setStatus(1);   // 只要在住的
        List<Household> list = householdService.queryByConditions(q);
        if (list.isEmpty()) return Result.fail("未找到该房号对应的在住住户");
        return Result.ok(toDTO(list.get(0)));
    }

    /** 按ID查住户简要信息（供 property-service 内部调用） */
    @GetMapping("/{id}/brief")
    public Result<HouseholdDTO> getBriefById(@PathVariable Integer id) {
        Household h = householdService.getById(id);
        if (h == null) return Result.fail("住户不存在");
        return Result.ok(toDTO(h));
    }

    // ==================== 内部原始接口（绕过 Result 泛型擦除问题） ====================

    /** 查询所有在住住户 — 原始数据（供 Feign 内部调用，不包装 Result） */
    @GetMapping("/active-raw")
    public List<HouseholdDTO> activeListRaw() {
        List<Household> list = householdService.queryByConditions(new HouseholdQueryDTO());
        return list.stream().filter(h -> h.getStatus() == 1).map(this::toDTO).collect(Collectors.toList());
    }

    /** 按ID查住户简要信息 — 原始数据 */
    @GetMapping("/{id}/brief-raw")
    public HouseholdDTO getBriefByIdRaw(@PathVariable Integer id) {
        Household h = householdService.getById(id);
        return h != null ? toDTO(h) : null;
    }

    /** 查询所有住户（含已搬离）— 供 property-service 查看历史缴费 */
    @GetMapping("/all-raw")
    public List<HouseholdDTO> allHouseholdsRaw() {
        List<Household> list = householdService.queryByConditions(new HouseholdQueryDTO());
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    /** 按住号搜索 — 原始数据 */
    @GetMapping("/search-by-room-raw")
    public HouseholdDTO searchByRoomRaw(@RequestParam String buildingNo,
                                         @RequestParam Integer floorNo,
                                         @RequestParam Integer unitNo) {
        HouseholdQueryDTO q = new HouseholdQueryDTO();
        q.setBuildingNo(buildingNo); q.setFloorNo(floorNo); q.setUnitNo(unitNo); q.setStatus(1);
        List<Household> list = householdService.queryByConditions(q);
        return list.isEmpty() ? null : toDTO(list.get(0));
    }

    /** Household 实体 → DTO */
    private HouseholdDTO toDTO(Household h) {
        HouseholdDTO dto = new HouseholdDTO();
        dto.setHouseholdId(h.getHouseholdId());
        dto.setBuildingNo(h.getBuildingNo());
        dto.setFloorNo(h.getFloorNo());
        dto.setUnitNo(h.getUnitNo());
        dto.setArea(h.getArea());
        dto.setPropertyFeeRate(h.getPropertyFeeRate());
        dto.setOwnerName(h.getOwnerName());
        dto.setStatus(h.getStatus());
        dto.setCheckInDate(h.getCheckInDate());
        dto.setCheckOutDate(h.getCheckOutDate());
        return dto;
    }

    /** 住户详情（含关联账号信息） */
    @GetMapping("/{id}/detail")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> detail(@PathVariable Integer id) {
        Household h = householdService.getById(id);
        if (h == null) return Result.fail("住户不存在");
        Map<String, Object> data = new HashMap<>();
        data.put("household", h);
        // 查关联账号
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getRefId, String.valueOf(id)).eq(User::getRole, "resident"));
        if (u != null) {
            Map<String, Object> account = new HashMap<>();
            account.put("userId", u.getUserId());
            account.put("username", u.getUsername());
            account.put("status", u.getStatus());
            data.put("account", account);
        }
        return Result.ok(data);
    }

    /**
     * 新增住户（入住登记），同时自动生成业主登录账号
     * POST /household
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> add(@Validated @RequestBody Household household) {
        try {
            User user = householdService.addHousehold(household);
            // 入住后自动生成当月物业费账单
            try {
                Map<String, Object> genBody = new HashMap<>();
                genBody.put("householdId", household.getHouseholdId());
                propertyFeignClient.generateBill(genBody);
            } catch (Exception e) {
                // 账单生成失败不阻塞入住流程，但需记录日志方便排查
                log.warn("入住后自动生成物业费账单失败，householdId={}，错误：{}", household.getHouseholdId(), e.getMessage());
            }
            Map<String, Object> data = new HashMap<>();
            data.put("username", user.getUsername());
            data.put("password", user.getPassword());   // 初始密码
            data.put("householdId", household.getHouseholdId());
            return Result.ok(data);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 修改住户信息
     * PUT /household
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> update(@Validated @RequestBody Household household) {
        if (household.getHouseholdId() == null) return Result.fail("住户ID不能为空");
        try {
            householdService.updateHouseholdWithValidate(household);
            return Result.ok("修改成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 住户搬离（仅改状态，不删数据）
     * PUT /household/{id}/move-out
     */
    /** 重置住户登录密码 */
    @PutMapping("/{id}/reset-pwd")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPwd(@PathVariable Integer id) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getRefId, String.valueOf(id)).eq(User::getRole, "resident"));
        if (u == null) return Result.fail("该住户无登录账号");
        u.setPassword(passwordEncoder.encode("123456"));
        userMapper.updateById(u);
        return Result.ok("密码已重置为 123456");
    }

    @PutMapping("/{id}/move-out")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> moveOut(@PathVariable Integer id) {
        try {
            householdService.moveOut(id);
            return Result.ok("搬离处理成功");
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }
}
