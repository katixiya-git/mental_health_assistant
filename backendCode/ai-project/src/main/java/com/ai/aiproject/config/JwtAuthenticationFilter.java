package com.ai.aiproject.config;

import com.ai.aiproject.Utils.JwtTool;
import com.ai.aiproject.Utils.ResponseWriteTool;
import com.ai.aiproject.common.SecurityConstants;
import com.ai.aiproject.enums.ResultCode;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.exceptions.TokenExpiredException;
import com.auth0.jwt.interfaces.DecodedJWT;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * JWT 认证过滤器：解析请求头中的 token，验证通过后把 userId 写入 SecurityContext。
 * 白名单路径跳过；认证失败由 ResponseWriteTool 写出统一 Result JSON。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证通过后，原始 JWT 存入 request attribute 的 key（供 controller 复用） */
    public static final String RAW_TOKEN_ATTR = "jwtRawToken";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtTool jwtTool;
    private final JwtConfig jwtConfig;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtTool jwtTool, JwtConfig jwtConfig, TokenBlacklist tokenBlacklist) {
        this.jwtTool = jwtTool;
        this.jwtConfig = jwtConfig;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        for (String url : SecurityConstants.PUBLIC_URLS) {
            if (PATH_MATCHER.match(url, path)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        // 兼容前端请求头：优先读取 "token" 头（前端 axios/SSE 使用），无则回退 Authorization 头（兼容 Bearer 前缀）
        String token = null;
        String tokenHeader = request.getHeader("token");
        if (tokenHeader != null && !tokenHeader.isBlank()) {
            token = tokenHeader.trim();
        } else {
            String header = request.getHeader(jwtConfig.getHeader());
            if (header != null && !header.isBlank()) {
                token = header;
                String prefix = jwtConfig.getTokenPrefix();
                if (prefix != null && !prefix.isBlank() && header.startsWith(prefix)) {
                    token = header.substring(prefix.length());
                }
            }
        }
        if (token == null) {
            ResponseWriteTool.writeError(response, ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
            return;
        }
        try {
            DecodedJWT jwt = jwtTool.parseToken(token.trim());
            // 黑名单校验：已登出的 token 一律拒绝
            if (tokenBlacklist.contains(token.trim())) {
                ResponseWriteTool.writeError(response, ResultCode.TOKEN_BLOCKED.getCode(), ResultCode.TOKEN_BLOCKED.getMsg());
                return;
            }
            Long userId = jwt.getClaim("userId").asLong();
            request.setAttribute(RAW_TOKEN_ATTR, token.trim());   // 透传原始 token 供 logout 使用
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (TokenExpiredException e) {
            ResponseWriteTool.writeError(response, ResultCode.TOKEN_EXPIRED.getCode(), ResultCode.TOKEN_EXPIRED.getMsg());
        } catch (JWTVerificationException e) {
            ResponseWriteTool.writeError(response, ResultCode.TOKEN_INVALID.getCode(), ResultCode.TOKEN_INVALID.getMsg());
        }
    }
}
