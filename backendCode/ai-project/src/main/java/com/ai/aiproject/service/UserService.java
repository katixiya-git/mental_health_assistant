package com.ai.aiproject.service;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.command.UserLoginCommandDTO;
import com.ai.aiproject.dto.command.UserRegisterCommandDTO;
import com.ai.aiproject.dto.response.UserLoginResponseDTO;

public interface UserService {
    /**
     * 用户登录
     * @param loginDTO 登录命令DTO
     * @return 登录响应DTO
     */
    UserLoginResponseDTO login(UserLoginCommandDTO loginDTO);
    /**
     * 用户注册
     * @param commandDTO 注册命令DTO
     * @return 注册响应DTO
     */
    UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO);
    /**
     * 获取当前登录用户信息
     * @return 用户详情DTO
     */
    UserLoginResponseDTO.UserDetailResponseDTO getCurrentUserInfo();
    /**
     * 用户登出：将当前 JWT 加入黑名单直至其过期
     * @param token 原始 JWT（过滤器已校验通过并透传）
     */
    void logout(String token);
}
