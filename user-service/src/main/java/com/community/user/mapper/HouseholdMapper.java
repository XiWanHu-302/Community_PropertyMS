package com.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.community.user.entity.Household;
import org.apache.ibatis.annotations.Mapper;

/**
 * 住户 Mapper —— 继承 BaseMapper 自带 CRUD
 * 多条件组合查询在 Service 层用 LambdaQueryWrapper 动态构建
 */
@Mapper
public interface HouseholdMapper extends BaseMapper<Household> {
}
