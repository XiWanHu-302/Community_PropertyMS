package com.community.property.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车位租用历史记录 — 每次释放车位时自动写入
 */
@TableName("parking_space_history")
public class ParkingSpaceHistory {
    @TableId(type = IdType.AUTO)
    private Integer id;
    private String spaceNo;          // 车位编号
    private Integer householdId;     // 住户ID（冻结快照）
    private String plateNo;          // 当时的车牌号
    private String ownerName;        // 冻结户主姓名
    private String room;             // 冻结房号（如 28-1301）
    private BigDecimal monthlyFee;   // 当时的月费
    private LocalDate assignedDate;  // 租用开始日期
    private LocalDate releasedDate;  // 释放日期

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public String getSpaceNo() { return spaceNo; }
    public void setSpaceNo(String spaceNo) { this.spaceNo = spaceNo; }
    public Integer getHouseholdId() { return householdId; }
    public void setHouseholdId(Integer householdId) { this.householdId = householdId; }
    public String getPlateNo() { return plateNo; }
    public void setPlateNo(String plateNo) { this.plateNo = plateNo; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }
    public BigDecimal getMonthlyFee() { return monthlyFee; }
    public void setMonthlyFee(BigDecimal monthlyFee) { this.monthlyFee = monthlyFee; }
    public LocalDate getAssignedDate() { return assignedDate; }
    public void setAssignedDate(LocalDate assignedDate) { this.assignedDate = assignedDate; }
    public LocalDate getReleasedDate() { return releasedDate; }
    public void setReleasedDate(LocalDate releasedDate) { this.releasedDate = releasedDate; }
}
