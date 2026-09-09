package com.ai.aiproject.service;

import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO;

/**
 * 数据看板统计服务
 */
public interface AnalyticsService {

    /** 管理员看板总览（近 7 天按日 + 全量/今日口径） */
    DataAnalyticsOverviewVO getOverview();
}
