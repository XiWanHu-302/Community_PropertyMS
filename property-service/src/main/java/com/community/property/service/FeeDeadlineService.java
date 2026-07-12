package com.community.property.service;

import com.community.property.config.DeadlineConfig;
import jakarta.annotation.Resource;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 缴费截止日服务 —— 物业费和停车费共用的截止日操作
 */
@Service
public class FeeDeadlineService {

    @Resource
    private DeadlineConfig deadlineConfig;

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 获取截止日 */
    public int getDeadlineDay() {
        return deadlineConfig.getDeadlineDay();
    }

    /** 获取截止日信息（供前端展示） */
    public Map<String, Object> getDeadlineInfo() {
        Map<String, Object> m = new HashMap<>();
        m.put("deadlineDay", deadlineConfig.getDeadlineDay());
        return m;
    }

    /**
     * 设置截止日（含校验和状态刷新）
     * @throws IllegalArgumentException 如果不在 1-28 范围内
     */
    @CacheEvict(value = "propertyFeeReport", allEntries = true)
    public void setDeadlineDay(int day) {
        if (day < 1 || day > 28) {
            throw new IllegalArgumentException("截止日必须在 1-28 之间");
        }
        deadlineConfig.setDeadlineDay(day);
        // 截止日变更后刷新本月待缴/逾期状态
        try {
            jdbcTemplate.update("CALL sp_refresh_after_deadline_change(?)", day);
        } catch (Exception e) { /* 存储过程可能尚未部署 */ }
    }

}
