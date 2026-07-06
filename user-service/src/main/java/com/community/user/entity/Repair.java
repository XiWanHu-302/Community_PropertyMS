package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 维修工单实体 —— 映射 repair 表
 * status: 0=待维修, 1=已完成, 2=已取消
 */
@TableName("repair")
public class Repair {

    @TableId(type = IdType.AUTO)
    private Integer repairId;           // 工单ID

    private Integer householdId;        // 住户ID

    private String content;             // 维修内容/问题描述

    private LocalDate reportDate;       // 报修日期

    private LocalDate repairDate;       // 维修完成日期（完工时填写）

    private BigDecimal amount;          // 维修金额（默认0）

    private Integer isFromFund;         // 是否从维修基金支出：0=自费, 1=基金支出

    private String repairPerson;        // 维修人姓名

    private Integer status;             // 0=待维修, 1=已完成, 2=已取消

    private LocalDateTime createTime;   // 创建时间

    // ========== getter / setter ==========

    public Integer getRepairId() { return repairId; }
    public void setRepairId(Integer repairId) { this.repairId = repairId; }

    public Integer getHouseholdId() { return householdId; }
    public void setHouseholdId(Integer householdId) { this.householdId = householdId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public LocalDate getReportDate() { return reportDate; }
    public void setReportDate(LocalDate reportDate) { this.reportDate = reportDate; }

    public LocalDate getRepairDate() { return repairDate; }
    public void setRepairDate(LocalDate repairDate) { this.repairDate = repairDate; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Integer getIsFromFund() { return isFromFund; }
    public void setIsFromFund(Integer isFromFund) { this.isFromFund = isFromFund; }

    public String getRepairPerson() { return repairPerson; }
    public void setRepairPerson(String repairPerson) { this.repairPerson = repairPerson; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
