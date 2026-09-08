package com.ai.aiproject.service.Impl;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.SecurityContextTool;
import com.ai.aiproject.Utils.UserConvertTool;
import com.ai.aiproject.Utils.JwtTool;
import com.ai.aiproject.config.TokenBlacklist;
import com.ai.aiproject.dto.command.UserLoginCommandDTO;
import com.ai.aiproject.dto.command.UserRegisterCommandDTO;
import com.ai.aiproject.dto.response.UserLoginResponseDTO;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.enums.UserType;
import com.ai.aiproject.mapper.UserMapper;
import com.ai.aiproject.service.UserService;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTool jwtUtil;
    private final TokenBlacklist tokenBlacklist;

    @Override
    public UserLoginResponseDTO login(UserLoginCommandDTO loginDTO) {
        // 1. 根据用户名或邮箱查询用户
        String account = loginDTO.getUsername();
        User user = userMapper.selectOne(
                Wrappers.<User>lambdaQuery()
                        .eq(User::getUsername, account)
                        .or()
                        .eq(User::getEmail, account)
        );
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), ResultCode.USER_NOT_EXIST.getMsg());
        }
        // 2. 使用 BCrypt 校验密码
        if (!passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_ERROR.getCode(), ResultCode.PASSWORD_ERROR.getMsg());
        }
        // 3. 校验用户状态（0 禁用 / 1 正常）
        if (user.isDisabled()) {
            throw new BusinessException(ResultCode.ACCOUNT_DISABLED.getCode(), ResultCode.ACCOUNT_DISABLED.getMsg());
        }
        // 4. 生成 JWT token
        String token = jwtUtil.generateToken(user);
        // 5. 返回统一响应
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = UserConvertTool.entityToDetailResponse(user);
        return UserConvertTool.buildLoginResponse(token, userInfo);
    }

    @Override
    public UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO) {
        // 1. 密码一致性校验
        if (!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())) {
            throw new BusinessException(ResultCode.PASSWORD_MISMATCH.getCode(), ResultCode.PASSWORD_MISMATCH.getMsg());
        }
        // 2. 用户名唯一性校验
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getUsername, commandDTO.getUsername())) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME.getCode(), ResultCode.ACCOUNT_SAME.getMsg());
        }
        // 3. 邮箱唯一性校验
        if (userMapper.selectCount(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, commandDTO.getEmail())) > 0) {
            throw new BusinessException(ResultCode.EMAIL_EXIST.getCode(), ResultCode.EMAIL_EXIST.getMsg());
        }
        // 4. 手机号唯一性校验（选填，非空时查重）
        if (commandDTO.getPhone() != null && !commandDTO.getPhone().isBlank()
                && userMapper.selectCount(Wrappers.<User>lambdaQuery()
                        .eq(User::getPhone, commandDTO.getPhone())) > 0) {
            throw new BusinessException(ResultCode.PHONE_EXIST.getCode(), ResultCode.PHONE_EXIST.getMsg());
        }
        // 5. 用户类型校验
        if (!UserType.isValidCode(commandDTO.getUserType())) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的用户类型");
        }
        // 6. BCrypt 加密密码，转实体，插入
        String encodedPassword = passwordEncoder.encode(commandDTO.getPassword());
        User user = UserConvertTool.registerCommandToEntity(commandDTO, encodedPassword);
        userMapper.insert(user);
        // 7. 返回注册用户详情（注册不自动登录，不生成 token）
        return UserConvertTool.entityToDetailResponse(user);
    }

    @Override
    public UserLoginResponseDTO.UserDetailResponseDTO getCurrentUserInfo() {
        // 当前用户 id 由 JwtAuthenticationFilter 写入 SecurityContext
        Long userId = SecurityContextTool.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), ResultCode.USER_NOT_EXIST.getMsg());
        }
        return UserConvertTool.entityToDetailResponse(user);
    }

    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            DecodedJWT jwt = jwtUtil.parseToken(token);
            tokenBlacklist.add(token, jwt.getExpiresAt().getTime());
        } catch (JWTVerificationException e) {
            // token 已过期或无效：无需拉黑（过滤器在有效 token 请求时才会放行到此处）
        }
    }
}
