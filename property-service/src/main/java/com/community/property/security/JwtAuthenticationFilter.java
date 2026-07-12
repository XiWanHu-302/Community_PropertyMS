package com.community.property.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证过滤器 —— 读取 Gateway 透传的用户信息头，设置 SecurityContext
 * <p>
 * Gateway 已完成 JWT 解析，通过 Header 传递 X-Username / X-User-Role / X-User-RefId。
 * 本 Filter 不再解析 JWT，只读取 Header 组装认证信息。
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String username = request.getHeader("X-Username");
        String role = request.getHeader("X-User-Role");
        String refId = request.getHeader("X-User-RefId");

        if (StringUtils.hasText(username) && StringUtils.hasText(role)) {
            Map<String, Object> details = new HashMap<>();
            if (StringUtils.hasText(refId)) {
                details.put("refId", refId);
            }

            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null,
                            Collections.singletonList(
                                    new SimpleGrantedAuthority("ROLE_" + role.toUpperCase())));
            auth.setDetails(details);  // refId 存入 details，供 Controller 读取
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }
}
