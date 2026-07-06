package com.community.user.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.user.entity.User;
import com.community.user.mapper.UserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Collections;

/**
 * 自定义 UserDetailsService —— 从数据库加载用户信息给 Spring Security
 */
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    @Resource
    private UserMapper userMapper;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // 从数据库查用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .eq(User::getUsername, username)
                        .eq(User::getStatus, 1)   // 只查启用状态的用户
        );

        if (user == null) {
            throw new UsernameNotFoundException("用户不存在或已被禁用：" + username);
        }

        // 角色前加 ROLE_ 前缀（Spring Security 规范）
        String roleName = "ROLE_" + user.getRole().toUpperCase();

        // 构建 Spring Security 的 UserDetails 对象
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority(roleName))
        );
    }
}
