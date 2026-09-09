package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO;
import com.ai.aiproject.service.AnalyticsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分析控制器（管理员）
 * <p>
 * GET /api/data-analytics/overview
 */
@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Resource
    private AnalyticsService analyticsService;

    @GetMapping("/overview")
    public Result<DataAnalyticsOverviewVO> overview() {
        return Result.ok(analyticsService.getOverview());
    }
}
