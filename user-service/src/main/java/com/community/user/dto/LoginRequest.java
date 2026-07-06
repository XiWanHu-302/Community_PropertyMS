package com.community.user.dto;

/**
 * 登录请求参数
 */
public class LoginRequest {

    private String username;   // 用户名
    private String password;   // 明文密码

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
