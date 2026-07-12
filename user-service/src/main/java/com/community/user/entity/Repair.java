package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 维修工单实体 —— 映射 repair 表
 * status: 0=待维修, 1=已完成, 2=已取消
 */
@Data
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

}
