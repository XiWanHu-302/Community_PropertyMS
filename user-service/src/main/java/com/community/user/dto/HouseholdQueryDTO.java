package com.community.user.dto;

/**
 * 住户多条件组合查询参数
 * 所有字段可选，前端传什么就按什么条件过滤
 */
public class HouseholdQueryDTO {

    private String buildingNo;      // 楼号（精确匹配，下拉选择）
    private Integer floorNo;        // 层号（精确）
    private Integer unitNo;         // 户号（精确）
    private String ownerName;       // 户主姓名（模糊）
    private String phone;           // 电话（模糊）
    private Integer status;         // 状态：1=在住, 0=已搬离

    public String getBuildingNo() { return buildingNo; }
    public void setBuildingNo(String buildingNo) { this.buildingNo = buildingNo; }

    public Integer getFloorNo() { return floorNo; }
    public void setFloorNo(Integer floorNo) { this.floorNo = floorNo; }

    public Integer getUnitNo() { return unitNo; }
    public void setUnitNo(Integer unitNo) { this.unitNo = unitNo; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
}
