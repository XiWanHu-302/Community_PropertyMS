package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.user.dto.LoginRequest;
import com.community.user.dto.LoginResponse;
import com.community.user.entity.Household;
import com.community.user.entity.MaintenanceStaff;
import com.community.user.entity.User;
import com.community.user.mapper.HouseholdMapper;
import com.community.user.mapper.MaintenanceStaffMapper;
import com.community.user.mapper.UserMapper;
import com.community.user.service.UserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Resource private UserService userService;
    @Resource private UserMapper userMapper;
    @Resource private HouseholdMapper householdMapper;
    @Resource private MaintenanceStaffMapper staffMapper;
    @Resource private PasswordEncoder passwordEncoder;

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        return Result.ok("登录成功", userService.login(request));
    }

    /** 当前用户信息 */
    @GetMapping("/me")
    public Result<Map<String, Object>> me(@AuthenticationPrincipal User user) {
        Map<String, Object> data = new HashMap<>();
        data.put("userId", user.getUserId());
        data.put("username", user.getUsername());
        data.put("role", user.getRole());
        data.put("refId", user.getRefId());
        if ("resident".equals(user.getRole()) && user.getRefId() != null) {
            Household h = householdMapper.selectById(Integer.valueOf(user.getRefId()));
            data.put("realName", h != null ? h.getOwnerName() : "");
            data.put("phone", h != null ? h.getPhone() : "");
        } else if ("maintenance".equals(user.getRole()) && user.getRefId() != null) {
            MaintenanceStaff s = staffMapper.selectById(user.getRefId());
            data.put("realName", s != null ? s.getRealName() : "");
            data.put("phone", s != null ? s.getPhone() : "");
        } else {
            data.put("realName", user.getUsername());
            data.put("phone", "");
        }
        return Result.ok(data);
    }

    /** 修改用户名 */
    @PutMapping("/update-username")
    public Result<Void> updateUsername(@AuthenticationPrincipal User cur, @RequestBody Map<String, String> body) {
        String n = body.get("username");
        if (!StringUtils.hasText(n)) return Result.fail("用户名不能为空");
        n = n.trim();
        Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, n).ne(User::getUserId, cur.getUserId()));
        if (c > 0) return Result.fail("用户名 " + n + " 已被占用");
        User u = userMapper.selectById(cur.getUserId());
        u.setUsername(n);
        userMapper.updateById(u);
        return Result.ok("修改成功");
    }

    /** 修改真实姓名 */
    @PutMapping("/update-realname")
    public Result<Void> updateRealName(@AuthenticationPrincipal User cur, @RequestBody Map<String, String> body) {
        String rn = body.get("realName");
        if (!StringUtils.hasText(rn)) return Result.fail("姓名不能为空");
        if ("maintenance".equals(cur.getRole()) && cur.getRefId() != null) {
            MaintenanceStaff s = staffMapper.selectById(cur.getRefId());
            if (s != null) { s.setRealName(rn.trim()); staffMapper.updateById(s); }
        } else if ("resident".equals(cur.getRole()) && cur.getRefId() != null) {
            Household h = householdMapper.selectById(Integer.valueOf(cur.getRefId()));
            if (h != null) { h.setOwnerName(rn.trim()); householdMapper.updateById(h); }
        }
        return Result.ok("修改成功");
    }

    /** 修改电话 */
    @PutMapping("/update-phone")
    public Result<Void> updatePhone(@AuthenticationPrincipal User cur, @RequestBody Map<String, String> body) {
        String phone = body.get("phone");
        if (phone != null && !phone.isEmpty() && !phone.matches("^1[3-9]\\d{9}$"))
            return Result.fail("请输入正确的11位手机号");
        if ("maintenance".equals(cur.getRole()) && cur.getRefId() != null) {
            MaintenanceStaff s = staffMapper.selectById(cur.getRefId());
            if (s != null) { s.setPhone(phone); staffMapper.updateById(s); }
        } else if ("resident".equals(cur.getRole()) && cur.getRefId() != null) {
            Household h = householdMapper.selectById(Integer.valueOf(cur.getRefId()));
            if (h != null) { h.setPhone(phone); householdMapper.updateById(h); }
        }
        return Result.ok("修改成功");
    }

    /** 修改密码 */
    @PutMapping("/update-password")
    public Result<Void> updatePassword(@AuthenticationPrincipal User cur, @RequestBody Map<String, String> body) {
        String oldPwd = body.get("oldPassword"), newPwd = body.get("newPassword");
        if (!StringUtils.hasText(oldPwd) || !StringUtils.hasText(newPwd)) return Result.fail("密码不能为空");
        if (newPwd.length() < 4) return Result.fail("新密码不能少于4位");
        User u = userMapper.selectById(cur.getUserId());
        if (!passwordEncoder.matches(oldPwd, u.getPassword())) return Result.fail("原密码错误");
        u.setPassword(passwordEncoder.encode(newPwd));
        userMapper.updateById(u);
        return Result.ok("密码修改成功");
    }

    /** 管理员修改任意用户名 */
    @PutMapping("/update-username-by-admin")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('ADMIN')")
    public Result<Void> updateUsernameByAdmin(@RequestBody Map<String, Object> body) {
        Integer userId = (Integer) body.get("userId");
        String newUsername = (String) body.get("username");
        if (userId == null || !StringUtils.hasText(newUsername)) return Result.fail("参数不能为空");
        Long c = userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, newUsername.trim()).ne(User::getUserId, userId));
        if (c > 0) return Result.fail("用户名 " + newUsername + " 已被占用");
        User u = userMapper.selectById(userId);
        if (u == null) return Result.fail("用户不存在");
        u.setUsername(newUsername.trim());
        userMapper.updateById(u);
        return Result.ok("修改成功");
    }
}
