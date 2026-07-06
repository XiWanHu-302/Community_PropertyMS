package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;

/**
 * 楼栋实体 —— 映射 building 表
 * 楼号直接做主键，不再另设自增ID
 */
@TableName("building")
public class Building {

    @TableId
    @NotBlank(message = "楼号不能为空")
    @Pattern(regexp = "^\\d{1,5}$", message = "楼号只能为数字，如：28")
    private String buildingNo;          // 楼号（主键），如 "28"

    @NotNull(message = "总层数不能为空")
    @Min(value = 1, message = "总层数必须 ≥ 1")
    private Integer floorCount;         // 总层数

    @NotNull(message = "每层户数不能为空")
    @Min(value = 1, message = "每层户数必须 ≥ 1")
    private Integer unitsPerFloor;      // 每层户数

    private LocalDateTime createTime;   // 创建时间

    // ========== getter / setter ==========

    public String getBuildingNo() {
        return buildingNo;
    }

    public void setBuildingNo(String buildingNo) {
        this.buildingNo = buildingNo;
    }

    public Integer getFloorCount() {
        return floorCount;
    }

    public void setFloorCount(Integer floorCount) {
        this.floorCount = floorCount;
    }

    public Integer getUnitsPerFloor() {
        return unitsPerFloor;
    }

    public void setUnitsPerFloor(Integer unitsPerFloor) {
        this.unitsPerFloor = unitsPerFloor;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}
