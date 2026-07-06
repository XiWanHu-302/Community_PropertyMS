package com.community.common.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 住户简要信息 DTO —— 用于跨服务传输
 * property-service 通过 OpenFeign 从 user-service 获取此数据
 * 只包含物业费/停车费服务需要的字段，不包含电话、工作单位等
 */
public class HouseholdDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Integer householdId;      // 住户ID
    private String buildingNo;        // 楼号
    private Integer floorNo;          // 层号
    private Integer unitNo;           // 户号
    private BigDecimal area;          // 面积（㎡）
    private BigDecimal propertyFeeRate; // 物业费单价（元/㎡/月）
    private String ownerName;         // 户主姓名
    private Integer status;           // 1=在住, 0=已搬离

    private LocalDate checkInDate;    // 入住日期（此日期之前的月份不产生费用）
    private LocalDate checkOutDate;   // 搬离日期（此日期之后的月份不产生费用）

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

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public LocalDate getCheckInDate() { return checkInDate; }
    public void setCheckInDate(LocalDate checkInDate) { this.checkInDate = checkInDate; }

    public LocalDate getCheckOutDate() { return checkOutDate; }
    public void setCheckOutDate(LocalDate checkOutDate) { this.checkOutDate = checkOutDate; }

    /**
     * 生成住号（如：28-1301 = 楼号-层号+户号）
     */
    public String getRoom() {
        if (buildingNo == null || floorNo == null || unitNo == null) return "";
        return buildingNo + "-" + floorNo + String.format("%02d", unitNo);
    }
}
