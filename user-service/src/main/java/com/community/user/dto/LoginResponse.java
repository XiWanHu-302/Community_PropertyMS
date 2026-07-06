package com.community.user.dto;

/**
 * 登录响应 —— 返回给前端
 */
public class LoginResponse {

    private String token;       // JWT Token
    private String role;        // 角色：admin / maintenance / resident
    private String realName;    // 真实姓名

    public LoginResponse() {}

    public LoginResponse(String token, String role, String realName) {
        this.token = token;
        this.role = role;
        this.realName = realName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getRealName() {
        return realName;
    }

    public void setRealName(String realName) {
        this.realName = realName;
    }
}
