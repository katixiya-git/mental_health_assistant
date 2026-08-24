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

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtTool jwtTool;
    private final JwtConfig jwtConfig;

    public JwtAuthenticationFilter(JwtTool jwtTool, JwtConfig jwtConfig) {
        this.jwtTool = jwtTool;
        this.jwtConfig = jwtConfig;
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
            Long userId = jwt.getClaim("userId").asLong();
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
