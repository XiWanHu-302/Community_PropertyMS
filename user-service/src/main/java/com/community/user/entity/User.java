package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体 —— 映射 user 表（纯认证）
 * ref_id 按角色指向不同表：
 *   resident    → household.household_id
 *   maintenance → maintenance_staff.worker_no
 *   admin       → NULL
 */
@Data
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

}
