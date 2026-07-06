package com.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.community.user.entity.Repair;
import org.apache.ibatis.annotations.Mapper;

/**
 * 维修工单 Mapper
 */
@Mapper
public interface RepairMapper extends BaseMapper<Repair> {
}
