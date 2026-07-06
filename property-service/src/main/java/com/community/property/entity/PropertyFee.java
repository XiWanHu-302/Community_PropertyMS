package com.community.property.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;

@TableName("property_fee")
public class PropertyFee {
    @TableId(type = IdType.AUTO)
    private Integer feeId;
    private Integer householdId;
    private Integer year;
    private Integer month;
    private BigDecimal amount;
    private Integer isPaid;       // 0=未缴, 1=已缴
    private LocalDate payDate;
    private String handler;
    private String billNo;

    // getter/setter
    public Integer getFeeId() { return feeId; }
    public void setFeeId(Integer feeId) { this.feeId = feeId; }
    public Integer getHouseholdId() { return householdId; }
    public void setHouseholdId(Integer householdId) { this.householdId = householdId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Integer getIsPaid() { return isPaid; }
    public void setIsPaid(Integer isPaid) { this.isPaid = isPaid; }
    public LocalDate getPayDate() { return payDate; }
    public void setPayDate(LocalDate payDate) { this.payDate = payDate; }
    public String getHandler() { return handler; }
    public void setHandler(String handler) { this.handler = handler; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
}
