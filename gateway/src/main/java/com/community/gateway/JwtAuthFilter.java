package com.community.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.community.common.JwtUtil;
import jakarta.annotation.Resource;

/**
 * 全局 JWT 过滤器 —— 解析 Token 并将用户信息写入请求头传给下游
 * <p>
 * 不做鉴权拦截（由各微服务的 Spring Security 负责），只负责提取用户信息。
 */
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    @Resource
    private JwtUtil jwtUtil;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String token = getToken(exchange.getRequest());
        if (StringUtils.hasText(token) && !jwtUtil.isTokenExpired(token)) {
            try {
                ServerHttpRequest mutated = exchange.getRequest().mutate()
                        .header("X-User-Id", String.valueOf(jwtUtil.getUserIdFromToken(token)))
                        .header("X-User-Role", jwtUtil.getRoleFromToken(token))
                        .header("X-Username", jwtUtil.getUsernameFromToken(token))
                        .header("X-User-RefId", jwtUtil.getRefIdFromToken(token))
                        .build();
                return chain.filter(exchange.mutate().request(mutated).build());
            } catch (Exception ignored) {
                // Token 解析失败，不传用户信息头
            }
        }
        return chain.filter(exchange);
    }

    private String getToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        return (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) ? bearer.substring(7) : null;
    }

    @Override
    public int getOrder() {
        return -100;
    }
}
