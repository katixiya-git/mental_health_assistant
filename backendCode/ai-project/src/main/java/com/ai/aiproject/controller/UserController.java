package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.config.JwtAuthenticationFilter;
import com.ai.aiproject.dto.command.UserLoginCommandDTO;
import com.ai.aiproject.dto.command.UserRegisterCommandDTO;
import com.ai.aiproject.dto.response.UserLoginResponseDTO;
import com.ai.aiproject.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/api/user")
public class UserController {
    @Resource
    private UserService userService;
    /**
     * 用户登录
     * <p>
     * POST /api/user/login
     *
     * @param loginDTO 登录请求参数（已通过 Spring Validation 校验）
     * @return 登录响应（token + 用户信息）
     */
    @PostMapping("/login")
    public Result<UserLoginResponseDTO> login(@Valid @RequestBody UserLoginCommandDTO loginDTO) {
        UserLoginResponseDTO dto = userService.login(loginDTO);
        return Result.ok(dto);
    }
    /**
     * 用户注册
     * <p>
     * POST /api/user/add
     *
     * @param commandDTO 注册请求参数（已通过 Spring Validation 校验）
     * @return 注册响应（用户详情）
     */
    @PostMapping("/add")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> register(@Valid @RequestBody UserRegisterCommandDTO commandDTO) {
        UserLoginResponseDTO.UserDetailResponseDTO dto = userService.register(commandDTO);
        return Result.ok(dto);
    }
    /**
     * 获取当前登录用户信息
     * <p>
     * GET /api/user/current
     *
     * @return 当前用户详情
     */
    @GetMapping("/current")
    public Result<UserLoginResponseDTO.UserDetailResponseDTO> current() {
        return Result.ok(userService.getCurrentUserInfo());
    }
    /**
     * 用户登出
     * <p>
     * POST /api/user/logout（需登录态；过滤器已把原始 token 写入 request attribute）
     *
     * @return 统一成功响应（幂等：无 token 也返回成功）
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute(JwtAuthenticationFilter.RAW_TOKEN_ATTR);
        if (token != null) {
            userService.logout(token);
        }
        return Result.ok();
    }
}
