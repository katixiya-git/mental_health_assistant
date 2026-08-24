package com.ai.aiproject.Utils;

import cn.hutool.json.JSONConfig;
import cn.hutool.json.JSONUtil;
import com.ai.aiproject.common.Result;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * 过滤器等非 Controller 场景下的响应写入工具类。
 * 用于把认证异常等包装为统一的 Result JSON 写入 HTTP 响应
 * （此类异常发生在 controller 之前，@RestControllerAdvice 捕获不到）。
 */
public class ResponseWriteTool {

    private ResponseWriteTool() {
    }

    /**
     * 认证失败统一写 HTTP 401 + Result JSON
     *
     * @param response HTTP 响应
     * @param code     Result 业务码
     * @param msg      错误消息
     */
    public static void writeError(HttpServletResponse response, String code, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(JSONUtil.toJsonStr(Result.error(code, msg, null),
                JSONConfig.create().setIgnoreNullValue(false)));
    }
}
