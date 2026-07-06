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
 * JWT 认证过滤器 —— 每个请求进来时，从 Header 中提取 Token 并解析用户身份
 * OncePerRequestFilter 保证每个请求只经过一次此过滤器
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserMapper userMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. 从请求头中获取 Token
        String token = getTokenFromRequest(request);

        // 2. 如果有 Token 且未过期，解析并设置认证信息
        if (StringUtils.hasText(token) && !jwtUtil.isTokenExpired(token)) {
            String username = jwtUtil.getUsernameFromToken(token);

            // 从数据库查用户（确保用户仍然存在且启用）
            User user = userMapper.selectOne(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getUsername, username)
                            .eq(User::getStatus, 1)
            );

            if (user != null) {
                String roleName = "ROLE_" + user.getRole().toUpperCase();

                // 构建认证令牌，存入 SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                user,                          // principal（用户信息）
                                null,                          // credentials（密码，Token 模式下不需要）
                                Collections.singletonList(
                                        new SimpleGrantedAuthority(roleName))
                        );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // 3. 放行，继续下一个过滤器
        filterChain.doFilter(request, response);
    }

    /**
     * 从请求头 Authorization 中提取 Token
     * 格式：Bearer xxxxx.yyyyy.zzzzz
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);   // 去掉 "Bearer " 前缀
        }
        return null;
    }
}
