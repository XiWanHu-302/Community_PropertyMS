package com.community.property.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.community.common.Result;
import com.community.common.dto.HouseholdDTO;
import com.community.property.entity.ParkingFee;
import com.community.property.entity.ParkingSpace;
import com.community.property.feign.UserServiceFeignClient;
import com.community.property.mapper.ParkingFeeMapper;
import com.community.property.mapper.ParkingSpaceMapper;
import com.community.property.config.DeadlineConfig;
import com.community.property.security.JwtUtil;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/parking-space")
public class ParkingSpaceController {

    @Resource private ParkingSpaceMapper spaceMapper;
    @Resource private ParkingFeeMapper feeMapper;
    @Resource private UserServiceFeignClient userFeignClient;
    @Resource private JwtUtil jwtUtil;                          // JWT 工具（解析 refId/role）
    @Resource private HttpServletRequest request;               // 获取请求头中的 Token
    @Resource private DeadlineConfig deadlineConfig;            // 截止日配置
    @Resource private JdbcTemplate jdbcTemplate;                // 存储过程调用

    /** 车位编号格式：字母 + 3位数字（如 A001） */
    private static final String SPACE_NO_PATTERN = "^[A-Z]\\d{3}$";

    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    public Result<List<Map<String, Object>>> list() {
        List<ParkingSpace> spaces = spaceMapper.selectList(null);
        List<Map<String, Object>> vos = new ArrayList<>();
        for (ParkingSpace s : spaces) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("spaceNo", s.getSpaceNo());
            vo.put("plateNo", s.getPlateNo());
            vo.put("monthlyFee", s.getMonthlyFee());
            vo.put("status", s.getStatus());
            vo.put("createTime", s.getCreateTime());   // 车位创建时间
            vo.put("assignedDate", s.getAssignedDate());  // 分配时间
            if (s.getHouseholdId() != null) {
                // 通过 OpenFeign 获取住户信息
                HouseholdDTO dto = userFeignClient.getBriefById(s.getHouseholdId());
                if (dto != null) {
                    vo.put("householdId", s.getHouseholdId());
                    vo.put("room", dto.getRoom());
                    vo.put("ownerName", dto.getOwnerName());
                }
            }
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    /** 新增车位（校验编号格式） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> add(@RequestBody ParkingSpace s) {
        if (!StringUtils.hasText(s.getSpaceNo()) || !s.getSpaceNo().matches(SPACE_NO_PATTERN))
            return Result.fail("车位编号格式不正确，请输入如 A001（字母+3位数字）");
        if (spaceMapper.selectById(s.getSpaceNo()) != null)
            return Result.fail("该车位编号已存在");
        spaceMapper.insert(s);
        return Result.ok("添加成功");
    }

    /** 修改车位信息 */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@RequestBody ParkingSpace s) {
        ParkingSpace db = spaceMapper.selectById(s.getSpaceNo());
        if (db == null) return Result.fail("车位不存在");
        if (s.getHouseholdId() != null && !s.getHouseholdId().equals(db.getHouseholdId())) {
            db.setHouseholdId(s.getHouseholdId()); db.setStatus(1);
            db.setAssignedDate(LocalDate.now());   // 住户变更时更新分配日期
        }
        if (s.getPlateNo() != null) db.setPlateNo(s.getPlateNo());
        if (s.getMonthlyFee() != null) db.setMonthlyFee(s.getMonthlyFee());
        spaceMapper.updateById(db);
        return Result.ok("修改成功");
    }

    /** 删除车位 — 检查是否有未缴停车费 */
    @DeleteMapping("/{spaceNo}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> delete(@PathVariable String spaceNo) {
        ParkingSpace s = spaceMapper.selectById(spaceNo);
        if (s == null) return Result.fail("车位不存在");
        // 检查未缴停车费
        List<ParkingFee> unpaid = feeMapper.selectList(
                new LambdaQueryWrapper<ParkingFee>()
                        .eq(ParkingFee::getSpaceNo, spaceNo)
                        .eq(ParkingFee::getIsPaid, 0));
        if (!unpaid.isEmpty()) {
            BigDecimal total = unpaid.stream().map(ParkingFee::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String months = unpaid.stream()
                    .map(f -> f.getYear() + "年" + f.getMonth() + "月")
                    .collect(Collectors.joining("、"));
            return Result.fail("该车位存在未缴停车费（" + months + "），共 ¥" + total + "，请先完成缴费再进行删除");
        }
        spaceMapper.deleteById(spaceNo);
        return Result.ok("删除成功");
    }

    /** 根据楼号+层号+户号查找住户（分配时用）— 通过 OpenFeign 调用 user-service */
    @GetMapping("/find-household")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Map<String, Object>> findHousehold(@RequestParam String buildingNo,
                                                      @RequestParam Integer floorNo,
                                                      @RequestParam Integer unitNo) {
        HouseholdDTO dto = userFeignClient.searchByRoom(buildingNo, floorNo, unitNo);
        if (dto == null) return Result.fail("未找到该房号对应的在住住户");
        Map<String, Object> data = new HashMap<>();
        data.put("householdId", dto.getHouseholdId());
        data.put("ownerName", dto.getOwnerName());
        data.put("room", dto.getRoom());
        return Result.ok(data);
    }

    /** 分配车位给住户（管理员+业主自助） */
    @PutMapping("/{spaceNo}/assign/{householdId}")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> assign(@PathVariable String spaceNo, @PathVariable Integer householdId,
                               @RequestParam(required = false) String plateNo) {
        // 安全校验：业主只能给自己分配车位
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        String role = jwtUtil.getRoleFromToken(token);
        if ("resident".equalsIgnoreCase(role)) {
            String refId = jwtUtil.getRefIdFromToken(token);
            if (refId == null || !refId.equals(String.valueOf(householdId))) {
                return Result.fail("只能为自己分配车位");
            }
        }

        ParkingSpace s = spaceMapper.selectById(spaceNo);
        if (s == null) return Result.fail("车位不存在");
        if (s.getStatus() != null && s.getStatus() == 1) return Result.fail("该车位已被租用");
        LocalDate today = LocalDate.now();
        s.setHouseholdId(householdId); s.setStatus(1);
        s.setAssignedDate(today);   // 记录分配日期
        if (StringUtils.hasText(plateNo)) s.setPlateNo(plateNo);
        spaceMapper.updateById(s);

        // 自动生成当前月停车费记录（防止分配后本月费用遗漏）
        ParkingFee existing = feeMapper.selectOne(
                new LambdaQueryWrapper<ParkingFee>()
                        .eq(ParkingFee::getSpaceNo, spaceNo)
                        .eq(ParkingFee::getYear, today.getYear())
                        .eq(ParkingFee::getMonth, today.getMonthValue()));
        if (existing == null) {
            ParkingFee newFee = new ParkingFee();
            newFee.setSpaceNo(spaceNo);
            newFee.setHouseholdId(householdId);
            newFee.setYear(today.getYear());
            newFee.setMonth(today.getMonthValue());
            newFee.setAmount(s.getMonthlyFee());
            // 根据截止日判定 is_paid：0待缴 或 -1逾期
            int isPaid = today.getDayOfMonth() > deadlineConfig.getDeadlineDay() ? -1 : 0;
            newFee.setIsPaid(isPaid);
            feeMapper.insert(newFee);
        }
        return Result.ok("租用成功");
    }

    /** 释放车位 — 检查未缴费用、写入历史记录、清空分配信息（管理员+业主自助） */
    @PutMapping("/{spaceNo}/release")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> release(@PathVariable String spaceNo) {
        ParkingSpace s = spaceMapper.selectById(spaceNo);
        if (s == null) return Result.fail("车位不存在");
        if (s.getHouseholdId() == null) return Result.fail("该车位当前为空闲状态，无需释放");

        // 安全校验：业主只能释放自己的车位
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) token = token.substring(7);
        String role = jwtUtil.getRoleFromToken(token);
        if ("resident".equalsIgnoreCase(role)) {
            String refId = jwtUtil.getRefIdFromToken(token);
            if (refId == null || !refId.equals(String.valueOf(s.getHouseholdId()))) {
                return Result.fail("只能释放自己的车位");
            }
        }

        // 1. 确保当前月有费用记录（释放前自动生成，防止漏检本月欠费）
        LocalDate today = LocalDate.now();
        ParkingFee currentFee = feeMapper.selectOne(
                new LambdaQueryWrapper<ParkingFee>()
                        .eq(ParkingFee::getSpaceNo, spaceNo)
                        .eq(ParkingFee::getYear, today.getYear())
                        .eq(ParkingFee::getMonth, today.getMonthValue()));
        if (currentFee == null) {
            ParkingFee newFee = new ParkingFee();
            newFee.setSpaceNo(spaceNo);
            newFee.setHouseholdId(s.getHouseholdId());
            newFee.setYear(today.getYear());
            newFee.setMonth(today.getMonthValue());
            newFee.setAmount(s.getMonthlyFee());
            int isPaid = today.getDayOfMonth() > deadlineConfig.getDeadlineDay() ? -1 : 0;
            newFee.setIsPaid(isPaid);
            feeMapper.insert(newFee);
        }

        // 2. 标记逾期后再检查未缴停车费（is_paid != 1，即 0待缴 + -1逾期）
        try { jdbcTemplate.update("CALL sp_mark_overdue(?)", deadlineConfig.getDeadlineDay()); } catch (Exception e) {}
        List<ParkingFee> unpaid = feeMapper.selectList(
                new LambdaQueryWrapper<ParkingFee>()
                        .eq(ParkingFee::getSpaceNo, spaceNo)
                        .ne(ParkingFee::getIsPaid, 1));
        if (!unpaid.isEmpty()) {
            BigDecimal total = unpaid.stream().map(ParkingFee::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String months = unpaid.stream()
                    .map(f -> f.getYear() + "年" + f.getMonth() + "月")
                    .collect(Collectors.joining("、"));
            return Result.fail("该车位存在未缴停车费（" + months + "），共 ¥" + total + "，请先完成缴费再进行释放");
        }

        // 3. 清空车位分配信息
        UpdateWrapper<ParkingSpace> uw = new UpdateWrapper<>();
        uw.eq("space_no", spaceNo)
          .set("household_id", null)
          .set("plate_no", null)
          .set("status", 0)
          .set("assigned_date", null);
        spaceMapper.update(null, uw);
        return Result.ok("已释放");
    }
}
