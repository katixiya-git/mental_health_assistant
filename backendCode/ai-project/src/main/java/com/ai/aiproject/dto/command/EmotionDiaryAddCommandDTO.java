package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增情绪日记命令 DTO
 */
@Data
public class EmotionDiaryAddCommandDTO {

    @NotNull(message = "记录日期不能为空")
    private LocalDate diaryDate;

    @NotNull(message = "情绪评分不能为空")
    @Min(value = 1, message = "情绪评分必须在1-10之间")
    @Max(value = 10, message = "情绪评分必须在1-10之间")
    private Integer moodScore;

    @Size(max = 20, message = "主要情绪长度不能超过20个字符")
    private String dominantEmotion;

    @Size(max = 1000, message = "情绪触发因素长度不能超过1000个字符")
    private String emotionTriggers;

    @Size(max = 2000, message = "日记内容长度不能超过2000个字符")
    private String diaryContent;

    @Min(value = 1, message = "睡眠质量必须在1-5之间")
    @Max(value = 5, message = "睡眠质量必须在1-5之间")
    private Integer sleepQuality;

    @Min(value = 1, message = "压力水平必须在1-5之间")
    @Max(value = 5, message = "压力水平必须在1-5之间")
    private Integer stressLevel;
}
