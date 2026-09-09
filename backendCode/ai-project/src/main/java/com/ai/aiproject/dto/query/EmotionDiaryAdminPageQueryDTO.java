package com.ai.aiproject.dto.query;

import lombok.Data;

/**
 * 管理端情绪日记分页查询 DTO
 */
@Data
public class EmotionDiaryAdminPageQueryDTO {

    private long current = 1;

    private long size = 10;

    /** 用户ID（字符串，宽松解析） */
    private String userId;

    /** 评分范围筛选，前端拼写 moodScreRange：'1-3'|'4-6'|'7-10' */
    private String moodScreRange;
}
