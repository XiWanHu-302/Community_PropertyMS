package com.community.property.config;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.time.Duration;
import java.util.UUID;

/**
 * Redis 分布式锁 —— SET NX EX 实现
 */
@Component
public class RedisLockUtil {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private final ThreadLocal<String> lockValue = new ThreadLocal<>();

    /**
     * 尝试获取锁
     *
     * @param key     锁的键
     * @param timeout 锁自动过期时间
     * @return true=获取成功, false=已被别人持有
     */
    public boolean tryLock(String key, Duration timeout) {
        String value = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, value, timeout);
        if (Boolean.TRUE.equals(ok)) {
            lockValue.set(value);
            return true;
        }
        return false;
    }

    /**
     * 释放锁（只有持有者才能释放）
     */
    public void unlock(String key) {
        String value = lockValue.get();
        if (value != null) {
            String current = stringRedisTemplate.opsForValue().get(key);
            if (value.equals(current)) {
                stringRedisTemplate.delete(key);
            }
            lockValue.remove();
        }
    }
}
