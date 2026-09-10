package com.ai.aiproject.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TokenBlacklist 单元测试。
 *
 * <p>全部用 Mockito 打桩 StringRedisTemplate，<b>不需要启动真实 Redis</b>，因此可以在 CI 里直接跑。
 * 这里重点验证三件事：TTL 取自 token 自身过期时刻、key 存的是摘要而非原始 JWT、
 * 以及 Redis 故障时的降级行为（不抛异常）。
 */
@ExtendWith(MockitoExtension.class)
class TokenBlacklistTest {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private TokenBlacklist blacklist() {
        return new TokenBlacklist(redis);
    }

    @Test
    void addThenContainsReturnsTrue() {
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.hasKey(anyString())).thenReturn(true);

        TokenBlacklist blacklist = blacklist();
        blacklist.add("t1", System.currentTimeMillis() + 60_000);

        verify(valueOps).set(anyString(), anyString(), any(Duration.class));
        assertTrue(blacklist.contains("t1"));
    }

    @Test
    void ttlComesFromTokenExpiryNotAFixedValue() {
        when(redis.opsForValue()).thenReturn(valueOps);

        blacklist().add("t1", System.currentTimeMillis() + 60_000);

        ArgumentCaptor<Duration> ttl = ArgumentCaptor.forClass(Duration.class);
        verify(valueOps).set(anyString(), anyString(), ttl.capture());
        // 允许测试执行耗时，TTL 应当接近 60 秒（这正是"用 JWT 的 exp 当 TTL"的意义）
        assertTrue(ttl.getValue().toMillis() > 59_000 && ttl.getValue().toMillis() <= 60_000,
                "TTL 应约等于 token 剩余有效期，实际: " + ttl.getValue());
    }

    @Test
    void alreadyExpiredTokenIsNotWritten() {
        // token 本身已过期：过滤器解析时就会拒绝，不必拉黑，也不该给 Redis 写一个负 TTL
        blacklist().add("t1", System.currentTimeMillis() - 1);

        verifyNoInteractions(redis);
    }

    @Test
    void blankOrNullTokenIsIgnored() {
        TokenBlacklist blacklist = blacklist();

        blacklist.add("   ", System.currentTimeMillis() + 60_000);
        blacklist.add(null, System.currentTimeMillis() + 60_000);

        assertFalse(blacklist.contains("   "));
        assertFalse(blacklist.contains(null));
        verifyNoInteractions(redis);
    }

    @Test
    void containsReturnsFalseWhenRedisIsDown() {
        // fail-open：Redis 抖动不能让全站请求 401
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertFalse(blacklist().contains("t1"));
    }

    @Test
    void addDoesNotThrowWhenRedisIsDown() {
        // fail-open：登出不应因为 Redis 故障而失败（但会打 ERROR 日志，见实现）
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));

        assertDoesNotThrow(() -> blacklist().add("t1", System.currentTimeMillis() + 60_000));
    }

    @Test
    void keyIsSha256DigestOfTokenNotTheRawToken() {
        String rawToken = "eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.signature-part";

        String key = TokenBlacklist.keyOf(rawToken);
        String digest = key.substring(TokenBlacklist.KEY_PREFIX.length());

        assertTrue(key.startsWith(TokenBlacklist.KEY_PREFIX));
        assertEquals(64, digest.length(), "SHA-256 十六进制应为 64 字符");
        assertTrue(digest.matches("[0-9a-f]{64}"), "摘要应全为小写十六进制");
        // 关键：原始凭证不能出现在 key 里（否则会泄露到 Redis 监控/慢日志）
        assertFalse(key.contains("eyJhbGciOiJIUzI1NiJ9"));

        // 同一个 token 稳定映射到同一个 key，不同 token 不冲突
        assertEquals(key, TokenBlacklist.keyOf(rawToken));
        assertNotEquals(key, TokenBlacklist.keyOf(rawToken + "x"));
    }
}
