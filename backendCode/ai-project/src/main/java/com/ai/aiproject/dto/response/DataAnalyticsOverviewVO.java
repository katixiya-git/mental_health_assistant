package com.ai.aiproject.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 数据看板总览响应 VO（键名与 dashboard.vue 严格一致）
 */
@Data
public class DataAnalyticsOverviewVO {

    private SystemOverviewVO systemOverview;
    private List<EmotionTrendItemVO> emotionTrend;
    private ConsultationStatsVO consultationStats;
    private List<UserActivityItemVO> userActivity;

    @Data
    public static class SystemOverviewVO {
        private Long totalUsers;
        private Long activeUsers;
        private Long totalDiaries;
        private Long todayNewDiaries;
        private Long totalSessions;
        private Long todayNewSessions;
        private Double avgMoodScore;
    }

    @Data
    public static class EmotionTrendItemVO {
        private String date;
        private Double avgMoodScore;
        private Integer recordCount;
    }

    @Data
    public static class ConsultationStatsVO {
        private Long totalSessions;
        private Integer avgDurationMinutes;
        private List<ConsultationDailyItemVO> dailyTrend;
    }

    @Data
    public static class ConsultationDailyItemVO {
        private String date;
        private Integer sessionCount;
        private Integer userCount;
    }

    @Data
    public static class UserActivityItemVO {
        private String date;
        private Integer activeUsers;
        private Integer newUsers;
        private Integer diaryUsers;
        private Integer consultationUsers;
    }
}
