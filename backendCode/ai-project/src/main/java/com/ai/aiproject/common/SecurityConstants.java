package com.ai.aiproject.common;

/**
 * 安全相关常量
 */
public final class SecurityConstants {

    /** 免认证白名单路径（SecurityConfig 与 JwtAuthenticationFilter 共用） */
    public static final String[] PUBLIC_URLS = {"/", "/api/test", "/api/user/login", "/api/user/add", "/error"};

    private SecurityConstants() {
    }
}
