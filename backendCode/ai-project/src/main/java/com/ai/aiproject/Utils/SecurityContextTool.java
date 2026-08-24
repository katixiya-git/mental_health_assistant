package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.enums.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 从 SecurityContext 获取当前登录用户信息的工具类
 * （userId 由 JwtAuthenticationFilter 认证后写入 SecurityContext）
 */
public class SecurityContextTool {

    private SecurityContextTool() {
    }

    /**
     * 获取当前登录用户 id
     *
     * @return 当前用户 id
     * @throws BusinessException 未认证（无 Authentication 或 principal）时抛 UNAUTHORIZED
     */
    public static Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED.getCode(), ResultCode.UNAUTHORIZED.getMsg());
        }
        return (Long) auth.getPrincipal();
    }
}
