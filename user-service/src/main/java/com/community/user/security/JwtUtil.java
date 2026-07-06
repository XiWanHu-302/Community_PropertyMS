package com.community.user.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT 工具类 —— 生成 Token、解析 Token、校验 Token
 * 基于 jjwt 0.12.x API，使用 HS256 签名算法
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;          // 签名密钥（原始字符串）

    @Value("${jwt.expiration}")
    private Long expiration;        // 过期时间（毫秒）

    /**
     * 将配置中的 secret 字符串转为 HMAC-SHA 签名密钥
     * Keys.hmacShaKeyFor() 会自动处理密钥长度不足的问题
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 JWT Token
     *
     * @param userId   用户ID
     * @param username 用户名
     * @param role     角色
     * @return JWT Token 字符串
     */
    public String generateToken(Integer userId, String username, String role, String refId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("username", username);
        claims.put("role", role);
        if (refId != null) claims.put("refId", refId);

        return Jwts.builder()
                .claims(claims)                                              // 载荷数据
                .subject(username)                                           // 主题（用户名）
                .issuedAt(new Date())                                        // 签发时间
                .expiration(new Date(System.currentTimeMillis() + expiration)) // 过期时间
                .signWith(getSigningKey(), Jwts.SIG.HS256)                   // HS256 签名
                .compact();
    }

    /**
     * 从 Token 中解析 Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验 Token 是否过期
     * @return true=未过期，false=已过期
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;   // 解析异常也视为过期
        }
    }

    /**
     * 从 Token 中获取 userId
     */
    public Integer getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Integer.class);
    }

    /**
     * 从 Token 中获取用户名
     */
    public String getUsernameFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从 Token 中获取角色
     */
    public String getRoleFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("role", String.class);
    }
}
