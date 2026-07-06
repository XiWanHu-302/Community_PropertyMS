package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.user.entity.MaintenanceStaff;
import com.community.user.entity.User;
import com.community.user.mapper.MaintenanceStaffMapper;
import com.community.user.mapper.UserMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 员工管理控制器
 */
@RestController
@RequestMapping("/staff")
public class StaffController {

    @Resource private MaintenanceStaffMapper staffMapper;
    @Resource private UserMapper userMapper;
    @Resource private PasswordEncoder passwordEncoder;

    /** 查询全部员工 */
    @GetMapping("/list")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<Map<String, Object>>> list() {
        List<MaintenanceStaff> list = staffMapper.selectList(new LambdaQueryWrapper<MaintenanceStaff>()
                .orderByAsc(MaintenanceStaff::getCreateTime));
        List<Map<String, Object>> vos = new ArrayList<>();
        for (MaintenanceStaff s : list) {
            Map<String, Object> vo = new HashMap<>();
            vo.put("workerNo", s.getWorkerNo());
            vo.put("realName", s.getRealName());
            vo.put("phone", s.getPhone());
            vo.put("status", s.getStatus());
            vo.put("createTime", s.getCreateTime());
            // 查关联用户账号
            User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .eq(User::getRefId, s.getWorkerNo()).eq(User::getRole, "maintenance"));
            vo.put("username", u != null ? u.getUsername() : "");
            vo.put("userId", u != null ? u.getUserId() : null);
            vo.put("userStatus", u != null ? u.getStatus() : null);
            vos.add(vo);
        }
        return Result.ok(vos);
    }

    /** 新增员工（+ 自动创建账号） */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Map<String, Object>> add(@RequestBody Map<String, Object> body) {
        // 生成唯一工号（精确到秒 + 查重兜底）
        String workerNo = "WX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        int seq = 0;
        while (staffMapper.selectById(workerNo) != null) {
            workerNo = "WX" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + "-" + (++seq);
        }
        MaintenanceStaff s = new MaintenanceStaff();
        s.setWorkerNo(workerNo);
        s.setRealName((String) body.getOrDefault("realName", ""));
        s.setPhone((String) body.getOrDefault("phone", ""));
        s.setStatus(1);
        staffMapper.insert(s);

        String uname = (String) body.get("username");
        if (!StringUtils.hasText(uname)) uname = "wx-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmm"));
        User u = new User();
        u.setUsername(uname);
        u.setPassword(passwordEncoder.encode("123456"));
        u.setRole("maintenance");
        u.setRefId(workerNo);
        u.setStatus(1);
        userMapper.insert(u);

        Map<String, Object> result = new HashMap<>();
        result.put("username", uname);
        result.put("workerNo", workerNo);
        return Result.ok(result);
    }

    /** 修改员工信息（含用户名） */
    @PutMapping("/{workerNo}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> update(@PathVariable String workerNo, @RequestBody Map<String, Object> body) {
        MaintenanceStaff s = staffMapper.selectById(workerNo);
        if (s == null) return Result.fail("员工不存在");
        if (body.containsKey("realName")) s.setRealName((String) body.get("realName"));
        if (body.containsKey("phone")) {
            String phone = (String) body.get("phone");
            if (phone != null && !phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$"))
                return Result.fail("请输入正确的11位手机号");
            s.setPhone(phone);
        }
        staffMapper.updateById(s);

        // 同步修改用户名
        String newUsername = (String) body.get("username");
        if (newUsername != null && !newUsername.isEmpty()) {
            User exist = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, newUsername));
            User cur = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getRefId, workerNo));
            if (cur != null) {
                if (exist != null && !exist.getUserId().equals(cur.getUserId()))
                    return Result.fail("用户名 " + newUsername + " 已被占用");
                cur.setUsername(newUsername);
                userMapper.updateById(cur);
            }
        }
        return Result.ok("修改成功");
    }

    /** 重置该员工的登录密码 */
    @PutMapping("/{workerNo}/reset-pwd")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPwd(@PathVariable String workerNo) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getRefId, workerNo));
        if (u == null) return Result.fail("该员工无登录账号");
        u.setPassword(passwordEncoder.encode("123456"));
        userMapper.updateById(u);
        return Result.ok("密码已重置为 123456");
    }

    /** 离职 */
    @PutMapping("/{workerNo}/disable")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> disable(@PathVariable String workerNo) {
        MaintenanceStaff s = staffMapper.selectById(workerNo);
        if (s == null) return Result.fail("员工不存在");
        s.setStatus(0); staffMapper.updateById(s);
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getRefId, workerNo));
        if (u != null) { u.setStatus(0); userMapper.updateById(u); }
        return Result.ok("已离职");
    }

    /** 复职 */
    @PutMapping("/{workerNo}/enable")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(rollbackFor = Exception.class)
    public Result<Void> enable(@PathVariable String workerNo) {
        MaintenanceStaff s = staffMapper.selectById(workerNo);
        if (s == null) return Result.fail("员工不存在");
        s.setStatus(1); staffMapper.updateById(s);
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getRefId, workerNo));
        if (u != null) { u.setStatus(1); userMapper.updateById(u); }
        return Result.ok("已复职");
    }
}
