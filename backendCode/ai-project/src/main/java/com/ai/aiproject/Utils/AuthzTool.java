package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.enums.UserType;
import com.ai.aiproject.mapper.UserMapper;

/**
 * 管理员鉴权工具（Service 层复用）
 */
public final class AuthzTool {

    private AuthzTool() {
    }

    /** 是否管理员；未登录/用户不存在一律返回 false */
    public static boolean isAdmin(UserMapper userMapper) {
        try {
            Long userId = SecurityContextTool.getCurrentUserId();
            User user = userMapper.selectById(userId);
            return user != null && UserType.ADMIN.getCode().equals(user.getUserType());
        } catch (BusinessException e) {
            return false;
        }
    }

    /** 非管理员抛 A0301 */
    public static void requireAdmin(UserMapper userMapper) {
        if (!isAdmin(userMapper)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(), ResultCode.ACCESS_UNAUTHORIZED.getMsg());
        }
    }
}
