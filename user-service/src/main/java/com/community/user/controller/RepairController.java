package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.user.entity.Household;
import com.community.user.entity.MaintenanceStaff;
import com.community.user.entity.Repair;
import com.community.user.entity.User;
import com.community.user.mapper.HouseholdMapper;
import com.community.user.mapper.MaintenanceStaffMapper;
import com.community.user.mapper.RepairMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 维修工单控制器 —— 支持管理员、维修员、住户三种角色
 * <p>
 * 状态流转：0(待维修) → 1(已完成) 或 → 2(已取消)
 * DB 触发器自动处理维修基金扣减（tr_repair_after_update）
 */
@RestController
@RequestMapping("/repair")
public class RepairController {

    @Resource private RepairMapper repairMapper;
    @Resource private HouseholdMapper householdMapper;
    @Resource private MaintenanceStaffMapper staffMapper;

    // ==================== 公共辅助：根据 household_id 获取住号 ====================

    /** 拼接住号：楼号-层号户号（如 28-1302） */
    private String buildRoom(Household h) {
        if (h == null) return "";
        return h.getBuildingNo() + "-" + h.getFloorNo() + String.format("%02d", h.getUnitNo());
    }

    /** 状态码 → 展示文本 */
    private String statusText(Integer status) {
        if (status == null) return "未知";
        switch (status) {
            case 0: return "待维修";
            case 1: return "已完成";
            case 2: return "已取消";
            default: return "未知";
        }
    }

    /** 将 Repair 实体 + 关联信息组装为前端用的 Map */
    private Map<String, Object> toVO(Repair r) {
        Map<String, Object> vo = new HashMap<>();
        vo.put("repairId", r.getRepairId());
        vo.put("householdId", r.getHouseholdId());
        vo.put("content", r.getContent());
        vo.put("reportDate", r.getReportDate() != null ? r.getReportDate().toString() : "");
        vo.put("repairDate", r.getRepairDate() != null ? r.getRepairDate().toString() : "");
        vo.put("amount", r.getAmount());
        vo.put("isFromFund", r.getIsFromFund());
        vo.put("repairPerson", r.getRepairPerson() != null ? r.getRepairPerson() : "");
        vo.put("status", r.getStatus());
        vo.put("statusText", statusText(r.getStatus()));
        vo.put("createTime", r.getCreateTime() != null ? r.getCreateTime().toString() : "");

        // 关联住户信息
        Household h = householdMapper.selectById(r.getHouseholdId());
        if (h != null) {
            vo.put("room", buildRoom(h));
            vo.put("buildingNo", h.getBuildingNo());
            vo.put("floorNo", h.getFloorNo());
            vo.put("unitNo", h.getUnitNo());
            vo.put("ownerName", h.getOwnerName());
            vo.put("phone", h.getPhone());
        } else {
            vo.put("room", "");
            vo.put("buildingNo", "");
            vo.put("floorNo", 0);
            vo.put("unitNo", 0);
            vo.put("ownerName", "");
            vo.put("phone", "");
        }
        return vo;
    }

    // ==================== 1. 住户提交报修 ====================

    /**
     * 住户提交报修申请
     * POST /repair
     * Body: { content: "问题描述" }
     * report_date 自动取当天
     */
    @PostMapping
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<Map<String, Object>> add(@AuthenticationPrincipal User user,
                                           @RequestBody Map<String, Object> body) {
        // 1. 通过当前登录用户的 ref_id 获取 household_id
        if (user.getRefId() == null) {
            return Result.fail("当前账号未关联住户信息");
        }
        Integer householdId = Integer.valueOf(user.getRefId());

        // 2. 校验住户存在且在住
        Household household = householdMapper.selectById(householdId);
        if (household == null || household.getStatus() != 1) {
            return Result.fail("住户信息不存在或已搬离");
        }

        // 3. 获取报修内容
        String content = (String) body.get("content");
        if (!StringUtils.hasText(content)) {
            return Result.fail("报修内容不能为空");
        }
        if (content.length() > 500) {
            return Result.fail("报修内容不能超过500字");
        }

        // 4. 创建工单
        Repair repair = new Repair();
        repair.setHouseholdId(householdId);
        repair.setContent(content.trim());
        repair.setReportDate(LocalDate.now());
        repair.setAmount(BigDecimal.ZERO);
        repair.setIsFromFund(0);
        repair.setStatus(0); // 待维修
        repairMapper.insert(repair);

        Map<String, Object> result = new HashMap<>();
        result.put("repairId", repair.getRepairId());
        result.put("message", "报修提交成功");
        return Result.ok(result);
    }

    // ==================== 2. 管理员：查询所有工单（多条件组合查询） ====================

    /**
     * 管理员查询所有维修工单（支持多条件筛选）
     * GET /repair/list?status=&keyword=&buildingNo=
     * keyword 可匹配：维修内容、户主姓名、住号
     */
    @GetMapping("/list")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Result<List<Map<String, Object>>> list(@RequestParam(required = false) Integer status,
                                                   @RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) String buildingNo) {
        LambdaQueryWrapper<Repair> w = new LambdaQueryWrapper<>();
        // 状态筛选：不传或传空则查全部
        if (status != null) {
            w.eq(Repair::getStatus, status);
        }
        // 关键字搜索：需要跨表（household），所以先查出所有再过滤
        // 楼号筛选同理
        w.orderByDesc(Repair::getCreateTime);

        List<Repair> list = repairMapper.selectList(w);
        List<Map<String, Object>> vos = new ArrayList<>();

        for (Repair r : list) {
            Map<String, Object> vo = toVO(r);

            // 关键字过滤（维修内容 / 户主姓名 / 住号）
            if (StringUtils.hasText(keyword)) {
                String kw = keyword.trim().toLowerCase();
                boolean match = (vo.get("content") != null && ((String) vo.get("content")).toLowerCase().contains(kw))
                        || (vo.get("ownerName") != null && ((String) vo.get("ownerName")).toLowerCase().contains(kw))
                        || (vo.get("room") != null && ((String) vo.get("room")).toLowerCase().contains(kw));
                if (!match) continue;
            }
            // 楼号过滤
            if (StringUtils.hasText(buildingNo)) {
                if (!buildingNo.equals(vo.get("buildingNo"))) continue;
            }

            vos.add(vo);
        }
        return Result.ok(vos);
    }

    // ==================== 3. 住户：查看自己的报修记录 ====================

    /**
     * 住户查看自己的报修记录
     * GET /repair/my?status=
     */
    @GetMapping("/my")
    @PreAuthorize("hasRole('RESIDENT')")
    public Result<List<Map<String, Object>>> myRepairs(@AuthenticationPrincipal User user,
                                                        @RequestParam(required = false) Integer status) {
        if (user.getRefId() == null) {
            return Result.fail("当前账号未关联住户信息");
        }
        Integer householdId = Integer.valueOf(user.getRefId());

        LambdaQueryWrapper<Repair> w = new LambdaQueryWrapper<Repair>()
                .eq(Repair::getHouseholdId, householdId)
                .orderByDesc(Repair::getCreateTime);
        if (status != null) {
            w.eq(Repair::getStatus, status);
        }

        List<Repair> list = repairMapper.selectList(w);
        List<Map<String, Object>> vos = list.stream().map(this::toVO).collect(Collectors.toList());
        return Result.ok(vos);
    }

    // ==================== 4. 维修员：查看分配给我的工单 ====================

    /**
     * 维修员查看分配给自己的工单
     * GET /repair/tasks?status=
     */
    @GetMapping("/tasks")
    @PreAuthorize("hasRole('MAINTENANCE')")
    public Result<List<Map<String, Object>>> myTasks(@AuthenticationPrincipal User user,
                                                      @RequestParam(required = false) Integer status) {
        // 通过 ref_id 查维修员姓名
        if (user.getRefId() == null) {
            return Result.fail("当前账号未关联维修员信息");
        }
        MaintenanceStaff staff = staffMapper.selectById(user.getRefId());
        if (staff == null) {
            return Result.fail("维修员信息不存在");
        }

        LambdaQueryWrapper<Repair> w = new LambdaQueryWrapper<Repair>()
                .eq(Repair::getRepairPerson, staff.getRealName())
                .orderByDesc(Repair::getCreateTime);
        if (status != null) {
            w.eq(Repair::getStatus, status);
        }

        List<Repair> list = repairMapper.selectList(w);
        List<Map<String, Object>> vos = list.stream().map(this::toVO).collect(Collectors.toList());
        return Result.ok(vos);
    }

    // ==================== 5. 查询单个工单详情 ====================

    /**
     * 查询单个维修工单详情
     * GET /repair/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE','RESIDENT')")
    public Result<Map<String, Object>> getById(@PathVariable Integer id) {
        Repair r = repairMapper.selectById(id);
        if (r == null) return Result.fail("工单不存在");
        return Result.ok(toVO(r));
    }

    // ==================== 6. 管理员/维修员：分配维修人员 ====================

    /**
     * 分配维修人员（将待维修工单指派给某位维修员）
     * PUT /repair/{id}/assign
     * Body: { repairPerson: "维修员老刘" }
     */
    @PutMapping("/{id}/assign")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> assign(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Repair r = repairMapper.selectById(id);
        if (r == null) return Result.fail("工单不存在");
        if (r.getStatus() != 0) return Result.fail("只有待维修状态的工单才能分配");

        String repairPerson = (String) body.get("repairPerson");
        if (!StringUtils.hasText(repairPerson)) {
            return Result.fail("请指定维修人员");
        }

        // 校验维修员存在且在职
        MaintenanceStaff staff = staffMapper.selectOne(new LambdaQueryWrapper<MaintenanceStaff>()
                .eq(MaintenanceStaff::getRealName, repairPerson.trim())
                .eq(MaintenanceStaff::getStatus, 1));
        if (staff == null) {
            return Result.fail("维修员 " + repairPerson + " 不存在或已离职");
        }

        r.setRepairPerson(repairPerson.trim());
        repairMapper.updateById(r);
        return Result.ok("已分配给 " + repairPerson.trim());
    }

    // ==================== 7. 维修员/管理员：标记完成 ====================

    /**
     * 标记维修完成
     * PUT /repair/{id}/complete
     * Body: { amount: 200.00, isFromFund: 0, repairDate: "2026-07-07" }
     * repairDate 默认当天；isFromFund=1 时 DB 触发器自动扣减维修基金余额
     */
    @PutMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> complete(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Repair r = repairMapper.selectById(id);
        if (r == null) return Result.fail("工单不存在");
        if (r.getStatus() != 0) return Result.fail("只有待维修状态的工单才能标记完成");

        // 维修金额
        Object amtObj = body.get("amount");
        if (amtObj != null) {
            BigDecimal amount;
            if (amtObj instanceof Number) {
                amount = BigDecimal.valueOf(((Number) amtObj).doubleValue());
            } else {
                try {
                    amount = new BigDecimal(amtObj.toString());
                } catch (NumberFormatException e) {
                    return Result.fail("金额格式不正确");
                }
            }
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                return Result.fail("维修金额不能为负数");
            }
            r.setAmount(amount);
        }

        // 是否从维修基金支出
        Integer isFromFund = (Integer) body.get("isFromFund");
        if (isFromFund != null) {
            if (isFromFund != 0 && isFromFund != 1) {
                return Result.fail("isFromFund 只能为 0 或 1");
            }
            r.setIsFromFund(isFromFund);
        }

        // 维修日期
        String repairDateStr = (String) body.get("repairDate");
        LocalDate repairDate;
        if (StringUtils.hasText(repairDateStr)) {
            try {
                repairDate = LocalDate.parse(repairDateStr);
            } catch (Exception e) {
                return Result.fail("维修日期格式不正确，请使用 yyyy-MM-dd 格式");
            }
        } else {
            repairDate = LocalDate.now();
        }
        // 维修日期不能早于报修日期（双重保障，DB 层也有触发器）
        if (repairDate.isBefore(r.getReportDate())) {
            return Result.fail("维修日期不能早于报修日期（" + r.getReportDate() + "）");
        }
        r.setRepairDate(repairDate);
        r.setStatus(1); // 已完成

        repairMapper.updateById(r);
        return Result.ok("维修已完成");
    }

    // ==================== 8. 管理员/住户：取消工单 ====================

    /**
     * 取消维修工单（仅待维修状态可取消）
     * PUT /repair/{id}/cancel
     */
    @PutMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> cancel(@PathVariable Integer id) {
        Repair r = repairMapper.selectById(id);
        if (r == null) return Result.fail("工单不存在");
        if (r.getStatus() != 0) return Result.fail("只有待维修状态的工单才能取消");

        r.setStatus(2); // 已取消
        repairMapper.updateById(r);
        return Result.ok("工单已取消");
    }

    // ==================== 9. 管理员：获取待分配工单数（供 Dashboard 使用） ====================

    /**
     * 获取各状态工单数量统计
     * GET /repair/stats
     */
    @GetMapping("/stats")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Result<Map<String, Object>> stats() {
        Map<String, Object> data = new HashMap<>();
        data.put("pendingCount", repairMapper.selectCount(
                new LambdaQueryWrapper<Repair>().eq(Repair::getStatus, 0)));
        data.put("completedCount", repairMapper.selectCount(
                new LambdaQueryWrapper<Repair>().eq(Repair::getStatus, 1)));
        data.put("cancelledCount", repairMapper.selectCount(
                new LambdaQueryWrapper<Repair>().eq(Repair::getStatus, 2)));
        return Result.ok(data);
    }

    // ==================== 10. 获取在职维修员列表（供分配下拉框使用） ====================

    /**
     * 获取在职维修员列表（供工单分配下拉框使用）
     * GET /repair/staff-list
     */
    @GetMapping("/staff-list")
    @PreAuthorize("hasAnyRole('ADMIN','MAINTENANCE')")
    public Result<List<Map<String, Object>>> staffList() {
        List<MaintenanceStaff> list = staffMapper.selectList(
                new LambdaQueryWrapper<MaintenanceStaff>()
                        .eq(MaintenanceStaff::getStatus, 1)
                        .orderByAsc(MaintenanceStaff::getCreateTime));
        List<Map<String, Object>> vos = list.stream().map(s -> {
            Map<String, Object> m = new HashMap<>();
            m.put("workerNo", s.getWorkerNo());
            m.put("realName", s.getRealName());
            m.put("phone", s.getPhone());
            return m;
        }).collect(Collectors.toList());
        return Result.ok(vos);
    }
}
