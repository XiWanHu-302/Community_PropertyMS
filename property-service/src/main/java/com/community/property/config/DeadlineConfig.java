package com.community.property.config;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;

/**
 * 缴费截止日配置（物业费和停车费共用，持久化到 system_config 表）
 * <p>
 * is_paid 三态定义：
 * <pre>
 *   -1 = 逾期（超过截止日仍未缴，永久保持直至缴费）
 *    0 = 待缴（当前月账单，在截止日内）
 *    1 = 已缴
 * </pre>
 */
@Component
public class DeadlineConfig {

    @Resource
    private JdbcTemplate jdbcTemplate;

    /** 默认截止日（每月几号） */
    private static final int DEFAULT_DEADLINE_DAY = 1;
    private static final String CONFIG_KEY = "deadline_day";

    /**
     * 获取当前截止日，若 DB 中无记录则返回默认值 1
     */
    public int getDeadlineDay() {
        try {
            Integer val = jdbcTemplate.queryForObject(
                "SELECT config_value FROM system_config WHERE config_key = ?",
                Integer.class, CONFIG_KEY);
            return val != null ? val : DEFAULT_DEADLINE_DAY;
        } catch (Exception e) {
            return DEFAULT_DEADLINE_DAY;
        }
    }

    /**
     * 设置截止日（仅允许 1-28），若 key 不存在则插入
     */
    public void setDeadlineDay(int deadlineDay) {
        int count = jdbcTemplate.update(
            "UPDATE system_config SET config_value = ? WHERE config_key = ?",
            String.valueOf(deadlineDay), CONFIG_KEY);
        if (count == 0) {
            // 首次写入
            jdbcTemplate.update(
                "INSERT INTO system_config (config_key, config_value) VALUES (?, ?)",
                CONFIG_KEY, String.valueOf(deadlineDay));
        }
    }
}
