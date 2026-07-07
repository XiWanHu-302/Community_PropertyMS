package com.community.property.config;

import com.community.property.security.JwtAuthenticationFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import jakarta.annotation.Resource;
import java.util.Arrays;

@Configuration
@EnableMethodSecurity   // 替代 @EnableGlobalMethodSecurity
public class SecurityConfig {

    @Resource
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 开启 CORS（property-service 被前端直接跨域调用）
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // 关闭 CSRF
            .csrf(csrf -> csrf.disable())

            // 无状态会话
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // 接口权限
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()   // OPTIONS 预检放行
                .requestMatchers("/hello").permitAll()
                .requestMatchers("/feign-test").permitAll()          // Feign 降级测试接口
                .requestMatchers("/property-fee/unpaid-raw/*").permitAll()   // 供 user-service 内部调用（搬离检查）
                .requestMatchers("/property-fee/generate").permitAll()      // 供 user-service 内部调用（入住生成账单）
                .anyRequest().authenticated()
            )

            // JWT 过滤器
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

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
