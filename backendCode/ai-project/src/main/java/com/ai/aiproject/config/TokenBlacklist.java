package com.ai.aiproject.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;

/**
 * 登出 token 黑名单（Redis 实现）。
 *
 * <p><b>语义</b>：logout 之后把该 JWT 拉黑，直到它自身过期为止。key 的 TTL 直接取自 JWT 的 exp，
 * 因此条目会随 token 自然过期被 Redis 自动回收，<b>不需要任何清理任务或定时任务</b>。
 *
 * <h3>为什么不用内存 Map（原实现）</h3>
 * <ol>
 *   <li><b>多实例不一致</b>：每个实例各持一份内存，A 实例登出后，B 实例仍会放行同一个 token。</li>
 *   <li><b>重启失效</b>：进程重启黑名单清空，所有"已登出"的 token 立刻复活。</li>
 *   <li><b>条目只增不减</b>：原实现只在 {@code contains()} 被调用时才惰性清理，而用户登出后前端就丢弃了
 *       token、不会再拿它请求 —— 也就是说那条记录永远等不到清理，会一直留在 Map 里。</li>
 * </ol>
 * 换成 Redis 后这三个问题一次性解决：多实例共享、重启不丢、TTL 由 Redis 自动回收。
 *
 * <h3>为什么 key 存摘要而不是原始 JWT</h3>
 * JWT 有几百字节，直接当 key 既浪费内存，又会让原始凭证出现在 Redis 监控、慢日志、{@code KEYS} 排查结果里。
 * 这里只存 SHA-256 摘要（64 个十六进制字符），判断存在性足够了。
 *
 * <h3>故障策略：黑名单不能让 Redis 变成单点故障</h3>
 * 读写两侧都做了降级，失败一律打 ERROR 日志（<b>不是静默忽略</b>），但都不向上抛：
 * <ul>
 *   <li>读（{@link #contains}）失败 → <b>放行</b>。主认证是 JWT 的签名与有效期，黑名单只是"提前撤销"的补充手段；
 *       若这里失败即拒绝，一次 Redis 抖动就会让全站请求 401。</li>
 *   <li>写（{@link #add}）失败 → 记录日志后返回。登出的主要动作是"客户端丢弃凭证"，那一步已经完成；
 *       把写黑名单失败升级成 5xx 会破坏统一响应契约，收益也不大。</li>
 * </ul>
 * 两者的代价相同：<b>Redis 故障期间，已登出的 token 仍然有效，最长到它自身过期（当前配置 24 小时）</b>。
 * 这个暴露窗口是有界的，配合 ERROR 日志告警 + Redis 高可用即可接受。
 * 如果业务要求"严格撤销、宁可不服务也不能放行"，把两个 catch 改成抛出异常即可（那是 fail-closed 的取舍）。
 */
@Slf4j
@Component
public class TokenBlacklist {

    /** Redis key 前缀：便于在 redis-cli 里按前缀排查（如 SCAN 0 MATCH auth:blacklist:*） */
    static final String KEY_PREFIX = "auth:blacklist:";

    /** 值不参与判断，"存在即已拉黑"；写常量是为了让排查时一眼看出是占位 */
    private static final String PLACEHOLDER = "1";

    private final StringRedisTemplate redis;

    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 拉黑一个 token，直到它自身过期为止。
     *
     * @param token      原始 JWT
     * @param expireAtMs token 的过期时刻（毫秒时间戳，取自 JWT 的 exp）
     */
    public void add(String token, long expireAtMs) {
        if (token == null || token.isBlank()) {
            return;
        }
        long ttlMillis = expireAtMs - System.currentTimeMillis();
        if (ttlMillis <= 0) {
            // token 本身已过期：过滤器解析时就会拒绝，无需拉黑（也避免 SET 一个非法的负 TTL）
            return;
        }
        try {
            // TTL 交给 Redis，到期自动回收 —— 这就是不需要清理任务的原因
            redis.opsForValue().set(keyOf(token), PLACEHOLDER, Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            // fail-open：不让登出因为 Redis 故障而失败；但必须留下 ERROR 让运维看得见
            log.error("Redis 写入失败，登出 token 未能加入黑名单（该 token 将一直有效到自身过期）", e);
        }
    }

    /**
     * 判断 token 是否已被拉黑。
     *
     * @return true 表示已登出、应当拒绝
     */
    public boolean contains(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redis.hasKey(keyOf(token)));
        } catch (Exception e) {
            // fail-open：黑名单是补充手段，不能因为 Redis 抖动让全站请求失败
            log.error("Redis 读取失败，token 黑名单校验降级放行（fail-open）", e);
            return false;
        }
    }

    /**
     * token → Redis key：只存 SHA-256 摘要，避免把原始凭证写进 key。
     * 包级可见是为了让单元测试能直接校验 key 的生成规则。
     */
    static String keyOf(String token) {
        return KEY_PREFIX + sha256Hex(token);
    }

    private static String sha256Hex(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 是 JDK 必须实现的算法，正常不可能走到这里
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", e);
        }
    }
}
