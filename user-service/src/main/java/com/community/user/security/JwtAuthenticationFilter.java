package com.community.user.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.user.entity.User;
import com.community.user.mapper.UserMapper;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.annotation.Resource;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * 认证过滤器 —— 读取 Gateway 透传的 X-Username 头，查库验证用户身份
 * <p>
 * Gateway 已完成 JWT 解析，通过 Header 传递用户信息。
 * 本 Filter 不再解析 JWT，只根据用户名查库确认用户存在且启用。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 从 Gateway 透传的 Header 中获取用户名
        String username = request.getHeader("X-Username");

        // 2. 如果有用户名，查数据库确认用户存在且启用
        if (StringUtils.hasText(username)) {
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username)
                            .eq(User::getStatus, 1)
            );

            if (user != null) {
                String roleName = "ROLE_" + user.getRole().toUpperCase();

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,                          // principal（完整 User 实体）
                                null,                          // credentials
                                Collections.singletonList(
                                        new SimpleGrantedAuthority(roleName))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 3. 放行
        filterChain.doFilter(request, response);
    }
}
