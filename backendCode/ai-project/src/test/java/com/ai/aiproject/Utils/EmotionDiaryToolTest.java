package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmotionDiaryToolTest {

    @Test
    void parseValidRange() {
        assertArrayEquals(new int[]{1, 3}, EmotionDiaryTool.parseMoodScoreRange("1-3"));
        assertArrayEquals(new int[]{4, 6}, EmotionDiaryTool.parseMoodScoreRange("4-6"));
        assertArrayEquals(new int[]{7, 10}, EmotionDiaryTool.parseMoodScoreRange("7-10"));
    }

    @Test
    void parseBlankRangeReturnsNull() {
        assertNull(EmotionDiaryTool.parseMoodScoreRange(null));
        assertNull(EmotionDiaryTool.parseMoodScoreRange("  "));
    }

    @Test
    void parseInvalidRangeThrows() {
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("abc"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("1-11"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("0-3"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("5-1"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("1-3-5"));
    }

    @Test
    void dominantEmotionValidation() {
        assertDoesNotThrow(() -> EmotionDiaryTool.validateDominantEmotion(null));
        assertDoesNotThrow(() -> EmotionDiaryTool.validateDominantEmotion(""));
        assertDoesNotThrow(() -> EmotionDiaryTool.validateDominantEmotion("开心"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.validateDominantEmotion("外星情绪"));
    }
}
