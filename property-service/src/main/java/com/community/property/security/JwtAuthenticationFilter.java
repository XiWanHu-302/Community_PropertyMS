package com.community.property.security;

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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Resource private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = getToken(request);
        if (StringUtils.hasText(token) && !jwtUtil.isTokenExpired(token)) {
            String username = jwtUtil.getUsernameFromToken(token);
            String role = "ROLE_" + jwtUtil.getRoleFromToken(token).toUpperCase();
            UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(username, null,
                            Collections.singletonList(new SimpleGrantedAuthority(role)));
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        chain.doFilter(request, response);
    }

    private String getToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) ? bearer.substring(7) : null;
    }
}
