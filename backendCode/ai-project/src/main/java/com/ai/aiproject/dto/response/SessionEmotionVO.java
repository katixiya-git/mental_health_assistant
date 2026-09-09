package com.ai.aiproject.dto.response;

import lombok.Data;

import java.util.List;

/**
 * 会话情绪分析 VO（键名与前端 consultation.vue currentEmotion 严格一致）
 */
@Data
public class SessionEmotionVO {

    private String primaryEmotion;

    private Integer emotionScore;

    private Boolean isNegative;

    private Integer riskLevel;

    private String suggestion;

    private List<String> improvementSuggestions;

    private String riskDescription;
}
