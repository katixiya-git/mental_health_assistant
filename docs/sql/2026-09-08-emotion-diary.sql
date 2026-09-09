-- 情绪日记表（人工执行：mysql -uroot -p123456 mental_health_assistant < 本文件）
CREATE TABLE IF NOT EXISTS emotion_diary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    diary_date DATE NOT NULL COMMENT '记录日期',
    mood_score TINYINT NOT NULL COMMENT '情绪评分 1-10',
    dominant_emotion VARCHAR(20) DEFAULT NULL COMMENT '主要情绪（中文）',
    emotion_triggers VARCHAR(1000) DEFAULT NULL COMMENT '情绪触发因素',
    diary_content VARCHAR(2000) DEFAULT NULL COMMENT '日记内容',
    sleep_quality TINYINT DEFAULT NULL COMMENT '睡眠质量 1-5',
    stress_level TINYINT DEFAULT NULL COMMENT '压力水平 1-5',
    ai_emotion_analysis TEXT COMMENT 'AI 情绪分析 JSON 字符串',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_date (user_id, diary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪日记表';
