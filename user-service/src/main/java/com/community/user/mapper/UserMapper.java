package com.community.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.community.user.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper —— MyBatis-Plus BaseMapper 自带 CRUD 方法
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
