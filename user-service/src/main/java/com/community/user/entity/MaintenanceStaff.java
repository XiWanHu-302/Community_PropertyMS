package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

/**
 * 维修员实体 —— 映射 maintenance_staff 表
 * 工号做主键，如 "WX202607041759"
 */
@TableName("maintenance_staff")
public class MaintenanceStaff {

    @TableId
    private String workerNo;            // 工号（PK），如 "WX202607041759"

    @NotBlank(message = "姓名不能为空")
    private String realName;            // 真实姓名

    @Pattern(regexp = "^(|1[3-9]\\d{9})$", message = "手机号格式不正确")
    private String phone;               // 电话（可选）

    private Integer status;             // 1=在职, 0=离职
    private LocalDateTime createTime;   // 创建时间

    // ========== getter / setter ==========

    public String getWorkerNo() { return workerNo; }
    public void setWorkerNo(String workerNo) { this.workerNo = workerNo; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
