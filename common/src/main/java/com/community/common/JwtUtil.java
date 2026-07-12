package com.community.common;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类（公共模块）—— Token 解析与校验
 * 所有微服务共用，只有 user-service 额外负责 Token 签发（见 TokenProvider）
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 解析 Token 中的 Claims */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 校验 Token 是否过期（true=未过期） */
    public boolean isTokenExpired(String token) {
        try {
            return parseToken(token).getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    /** 从 Token 中获取 userId */
    public Integer getUserIdFromToken(String token) {
        return parseToken(token).get("userId", Integer.class);
    }

    /** 从 Token 中获取用户名 */
    public String getUsernameFromToken(String token) {
        return parseToken(token).getSubject();
    }

    /** 从 Token 中获取角色 */
    public String getRoleFromToken(String token) {
        return parseToken(token).get("role", String.class);
    }

    /** 从 Token 中获取 refId（业主为 householdId，维修员为 workerNo） */
    public String getRefIdFromToken(String token) {
        return parseToken(token).get("refId", String.class);
    }
}
