package com.community.user.config;

import com.community.user.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.Resource;
import java.util.Arrays;

/**
 * Spring Security 配置类（Spring Boot 3.x / Security 6.x 版本）
 */
@Configuration
@EnableMethodSecurity   // 替代 @EnableGlobalMethodSecurity，prePostEnabled=true 是默认值
public class SecurityConfig {

    @Resource
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * BCrypt 密码编码器 —— 用于密码加密和校验
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * AuthenticationManager —— 登录认证时使用
     */
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authConfig) throws Exception {
        return authConfig.getAuthenticationManager();
    }

    /**
     * 安全过滤链 —— 核心配置（lambda DSL）
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 开启 CORS（与 property-service 保持一致）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 关闭 CSRF（前后端分离 + JWT 不需要）
            .csrf(csrf -> csrf.disable())

            // 无状态会话（不创建 HttpSession，全靠 JWT）
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 接口权限配置
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // OPTIONS 预检放行
                .requestMatchers("/auth/login").permitAll()   // 登录接口放行
                .requestMatchers("/hello").permitAll()        // 测试接口放行
                // 内部接口：供 property-service 通过 OpenFeign 调用（只读查询，等同直接查DB）
                .requestMatchers("/household/active").permitAll()
                .requestMatchers("/household/active-raw").permitAll()
                .requestMatchers("/household/all-raw").permitAll()
                .requestMatchers("/household/search-by-room").permitAll()
                .requestMatchers("/household/search-by-room-raw").permitAll()
                .requestMatchers("/household/*/brief").permitAll()
                .requestMatchers("/household/*/brief-raw").permitAll()
                .anyRequest().authenticated()                 // 其余接口全部需要认证
            )

            // 把 JWT 过滤器加到 UsernamePasswordAuthenticationFilter 之前
            .addFilterBefore(jwtAuthenticationFilter,
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 跨域配置 —— 允许所有来源、方法、请求头
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.asList("*"));
        config.setAllowedMethods(Arrays.asList("*"));
        config.setAllowedHeaders(Arrays.asList("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
