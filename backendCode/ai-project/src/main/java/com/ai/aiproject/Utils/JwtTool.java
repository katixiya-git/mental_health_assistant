package com.ai.aiproject.Utils;

import com.ai.aiproject.config.JwtConfig;
import com.ai.aiproject.entity.User;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * JWT 工具类：生成与解析登录令牌
 */
@Component
@RequiredArgsConstructor
public class JwtTool {

    private final JwtConfig jwtConfig;

    /**
     * 生成登录令牌
     *
     * @param user 登录用户
     * @return 原始 JWT（不含 "Bearer " 前缀，前缀由客户端/过滤器组装）
     */
    public String generateToken(User user) {
        Date now = new Date();
        Date expiresAt = new Date(now.getTime() + jwtConfig.getExpiration());
        Algorithm algorithm = Algorithm.HMAC256(jwtConfig.getSecret());
        return JWT.create()
                .withClaim("userId", user.getId())
                .withClaim("username", user.getUsername())
                .withClaim("userType", user.getUserType())
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
    }

    /**
     * 解析并校验令牌（供后续 JWT 过滤器使用）
     *
     * @param token 原始 JWT
     * @return 解码后的 JWT
     */
    public DecodedJWT parseToken(String token) {
        return JWT.require(Algorithm.HMAC256(jwtConfig.getSecret())).build().verify(token);
    }
}
