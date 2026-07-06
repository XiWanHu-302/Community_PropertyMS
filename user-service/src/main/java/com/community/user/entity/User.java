package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 映射 user 表（纯认证）
 * ref_id 按角色指向不同表：
 *   resident    → household.household_id
 *   maintenance → maintenance_staff.worker_no
 *   admin       → NULL
 */
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Integer userId;
    private String username;
    private String password;
    private String role;              // admin / maintenance / resident
    private String refId;             // 关联ID（按角色含义不同）
    private Integer status;           // 1=启用, 0=禁用
    private LocalDateTime createTime;

    // ========== getter / setter ==========
    public Integer getUserId() { return userId; }
    public void setUserId(Integer userId) { this.userId = userId; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
    public String getRefId() { return refId; }
    public void setRefId(String refId) { this.refId = refId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
