package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.enums.ResultCode;

import java.util.Set;

/**
 * 情绪日记模块纯静态工具（便于无 Spring/Mockito 的单元测试）
 */
public final class EmotionDiaryTool {

    /** 前端可选的主要情绪（与 emotionDiary.vue emotionOptions 一致） */
    private static final Set<String> DOMINANT_EMOTIONS =
            Set.of("开心", "平静", "焦虑", "悲伤", "兴奋", "疲惫", "惊讶", "困惑");

    private EmotionDiaryTool() {
    }

    /**
     * 校验主要情绪：空值放行，非空必须命中前端枚举
     */
    public static void validateDominantEmotion(String dominantEmotion) {
        if (dominantEmotion != null && !dominantEmotion.isBlank() && !DOMINANT_EMOTIONS.contains(dominantEmotion)) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的主要情绪");
        }
    }

    /**
     * 解析前端评分范围筛选（'1-3'/'4-6'/'7-10'）
     *
     * @return [low, high]；空值返回 null；非法值抛 PARAM_INVALID
     */
    public static int[] parseMoodScoreRange(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String[] parts = range.split("-");
        if (parts.length != 2) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的情绪评分范围");
        }
        int low;
        int high;
        try {
            low = Integer.parseInt(parts[0].trim());
            high = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的情绪评分范围");
        }
        if (low < 1 || high > 10 || low > high) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的情绪评分范围");
        }
        return new int[]{low, high};
    }
}
