package com.community.user.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.community.user.entity.Building;
import com.community.user.mapper.BuildingMapper;
import com.community.user.service.BuildingService;
import org.springframework.stereotype.Service;

/**
 * 楼栋服务实现 —— 继承 ServiceImpl 自带 CRUD 方法
 */
@Service
public class BuildingServiceImpl
        extends ServiceImpl<BuildingMapper, Building>
        implements BuildingService {
}
