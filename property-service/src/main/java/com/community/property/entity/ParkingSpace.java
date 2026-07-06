package com.community.property.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@TableName("parking_space")
public class ParkingSpace {
    @TableId
    private String spaceNo;
    private Integer householdId;
    private String plateNo;
    private BigDecimal monthlyFee;
    private Integer status;       // 1=已租, 0=空闲
    private LocalDateTime createTime;   // 车位创建时间（数据库自动填充）
    private LocalDate assignedDate;     // 当前租户分配日期，释放时清空

    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }
    public Integer getHouseholdId() { return householdId; }
    public void setHouseholdId(Integer householdId) { this.householdId = householdId; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDate getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }
}
