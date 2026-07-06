package com.community.user.util;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 临时工具 —— 生成 BCrypt 密文
 * 运行 main 方法后，把输出的密文复制到 init.sql 替换占位符
 */
public class PasswordGen {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String raw = "123456";
        String encoded = encoder.encode(raw);
        System.out.println("明文: " + raw);
        System.out.println("密文: " + encoded);
    }
}
