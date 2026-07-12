package com.community.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.user.dto.LoginRequest;
import com.community.user.dto.LoginResponse;
import com.community.user.entity.Household;
import com.community.user.entity.MaintenanceStaff;
import com.community.user.entity.User;
import com.community.user.mapper.HouseholdMapper;
import com.community.user.mapper.MaintenanceStaffMapper;
import com.community.user.mapper.UserMapper;
import com.community.user.security.TokenProvider;
import com.community.user.service.UserService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;

@Service
public class UserServiceImpl implements UserService {

    @Resource private UserMapper userMapper;
    @Resource private HouseholdMapper householdMapper;
    @Resource private MaintenanceStaffMapper staffMapper;
    @Resource private AuthenticationManager authenticationManager;
    @Resource private TokenProvider tokenProvider;

    @Override
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()).eq(User::getStatus, 1));
        if (user == null) throw new BadCredentialsException("用户不存在或已被禁用");

        String realName = user.getUsername();
        if ("resident".equals(user.getRole()) && user.getRefId() != null) {
            Household h = householdMapper.selectById(Integer.valueOf(user.getRefId()));
            if (h != null) realName = h.getOwnerName();
        } else if ("maintenance".equals(user.getRole()) && user.getRefId() != null) {
            MaintenanceStaff s = staffMapper.selectById(user.getRefId());
            if (s != null) realName = s.getRealName();
        }
        String token = tokenProvider.generateToken(user.getUserId(), user.getUsername(), user.getRole(), user.getRefId());
        return new LoginResponse(token, user.getRole(), realName);
    }
}
