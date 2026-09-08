package com.ai.aiproject.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 黑名单：logout 后将 JWT 拉黑至其过期时刻。
 * 单实例有效、重启失效（无持久化）；条目在 contains() 时惰性清理。
 * 后续如需集群共享/持久化，可替换为 Redis 实现（SETEX + EXISTS），调用方不变。
 */
@Component
public class TokenBlacklist {

    /** token -> 过期时刻（毫秒时间戳，取自 JWT exp） */
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    public void add(String token, long expireAtMs) {
        if (expireAtMs > System.currentTimeMillis()) {
            blacklist.put(token, expireAtMs);
        }
    }

    public boolean contains(String token) {
        Long expireAtMs = blacklist.get(token);
        if (expireAtMs == null) {
            return false;
        }
        if (expireAtMs <= System.currentTimeMillis()) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    public void remove(String token) {
        blacklist.remove(token);
    }
}
