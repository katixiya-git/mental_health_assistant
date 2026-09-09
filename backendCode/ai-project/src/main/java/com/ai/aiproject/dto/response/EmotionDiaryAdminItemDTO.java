package com.ai.aiproject.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端情绪日记列表/详情行 DTO
 */
@Data
public class EmotionDiaryAdminItemDTO {

    private Long id;

    private Long userId;

    private String username;

    private String nickname;

    private LocalDate diaryDate;

    private Integer moodScore;

    private String dominantEmotion;

    private Integer sleepQuality;

    private Integer stressLevel;

    private String emotionTriggers;

    private String diaryContent;

    /** AI 情绪分析 JSON 字符串（前端 JSON.parse） */
    private String aiEmotionAnalysis;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
