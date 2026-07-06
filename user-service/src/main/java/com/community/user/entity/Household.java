package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 住户实体 —— 映射 household 表
 */
@TableName("household")
public class Household {

    @TableId(type = IdType.AUTO)
    private Integer householdId;        // 住户ID

    @NotBlank(message = "楼号不能为空")
    private String buildingNo;          // 楼号

    @NotNull(message = "层号不能为空")
    @Min(value = 1, message = "层号必须 ≥ 1")
    private Integer floorNo;            // 层号

    @NotNull(message = "户号不能为空")
    @Min(value = 1, message = "户号必须 ≥ 1")
    private Integer unitNo;             // 户号

    @NotNull(message = "面积不能为空")
    @DecimalMin(value = "0.01", message = "面积必须 > 0")
    private BigDecimal area;            // 面积（平方米）

    @NotNull(message = "物业费单价不能为空")
    @DecimalMin(value = "0.01", message = "物业费单价必须 > 0")
    private BigDecimal propertyFeeRate; // 物业费单价（元/㎡/月）

    @NotBlank(message = "户主姓名不能为空")
    private String ownerName;           // 户主姓名

    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$",
             message = "请输入正确的11位手机号")
    private String phone;               // 电话

    private String workUnit;            // 工作单位

    private Integer familySize;         // 家庭人数

    private BigDecimal repairFundBalance; // 维修基金余额

    private Integer status;             // 1=在住, 0=已搬离

    private LocalDate checkInDate;      // 入住日期

    private LocalDate checkOutDate;     // 搬离日期

    private LocalDateTime createTime;   // 创建时间

    // ========== getter / setter ==========

    public Integer getHouseholdId() { return householdId; }
    public void setHouseholdId(Integer householdId) { this.householdId = householdId; }

    public String getBuildingNo() { return buildingNo; }
    public void setBuildingNo(String buildingNo) { this.buildingNo = buildingNo; }

    public Integer getFloorNo() { return floorNo; }
    public void setFloorNo(Integer floorNo) { this.floorNo = floorNo; }

    public Integer getUnitNo() { return unitNo; }
    public void setUnitNo(Integer unitNo) { this.unitNo = unitNo; }

    public BigDecimal getArea() { return area; }
    public void setArea(BigDecimal area) { this.area = area; }

    public BigDecimal getPropertyFeeRate() { return propertyFeeRate; }
    public void setPropertyFeeRate(BigDecimal propertyFeeRate) { this.propertyFeeRate = propertyFeeRate; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getWorkUnit() { return workUnit; }
    public void setWorkUnit(String workUnit) { this.workUnit = workUnit; }

    public Integer getFamilySize() { return familySize; }
    public void setFamilySize(Integer familySize) { this.familySize = familySize; }

    public BigDecimal getRepairFundBalance() { return repairFundBalance; }
    public void setRepairFundBalance(BigDecimal repairFundBalance) { this.repairFundBalance = repairFundBalance; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }

    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
