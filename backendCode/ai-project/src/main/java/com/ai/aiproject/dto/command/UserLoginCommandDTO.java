package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户登录请求 DTO
 */
@Data
public class UserLoginCommandDTO {

    /**
     * 用户名或邮箱
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3到50个字符之间")
    private String username;

    /**
     * 密码
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 255, message = "密码长度必须在6到255个字符之间")
    private String password;
}
