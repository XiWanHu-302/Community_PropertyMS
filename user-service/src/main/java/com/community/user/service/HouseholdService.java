package com.community.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.community.user.dto.HouseholdQueryDTO;
import com.community.user.entity.Household;
import com.community.user.entity.User;

import java.util.List;

/**
 * 住户服务接口
 */
public interface HouseholdService extends IService<Household> {

    /**
     * 多条件组合查询住户
     */
    List<Household> queryByConditions(HouseholdQueryDTO query);

    /**
     * 新增住户（含房号合法性校验），同时自动生成业主登录账号
     * @return 自动创建的用户对象（含用户名和初始密码）
     */
    User addHousehold(Household household);

    /**
     * 修改住户（含房号合法性校验）
     */
    boolean updateHouseholdWithValidate(Household household);

    /**
     * 住户搬离（仅改状态，不删数据）
     */
    boolean moveOut(Integer householdId);
}
