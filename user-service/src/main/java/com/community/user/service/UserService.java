package com.community.user.service;

import com.community.user.dto.LoginRequest;
import com.community.user.dto.LoginResponse;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 用户登录
     * @param request 用户名 + 明文密码
     * @return JWT Token + 角色 + 真实姓名
     */
    LoginResponse login(LoginRequest request);
}
