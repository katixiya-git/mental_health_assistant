package com.ai.aiproject.mapper;

import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 数据看板聚合查询 Mapper（原生 SQL 只读统计，返回 Map；被 @MapperScan 覆盖注册）
 */
public interface AnalyticsMapper {

    /** 近 N 天日记按日：cnt、avgscore、diaryusers */
    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS cnt, AVG(mood_score) AS avgscore, COUNT(DISTINCT user_id) AS diaryusers " +
            "FROM emotion_diary WHERE created_at >= #{start} GROUP BY DATE(created_at)")
    List<Map<String, Object>> diaryDaily(LocalDateTime start);

    /** 近 N 天会话按日（按创建时间）：cnt、ucnt */
    @Select("SELECT DATE(started_at) AS day, COUNT(*) AS cnt, COUNT(DISTINCT user_id) AS ucnt " +
            "FROM consultation_session WHERE started_at >= #{start} GROUP BY DATE(started_at)")
    List<Map<String, Object>> sessionDaily(LocalDateTime start);

    /** 近 N 天注册用户按日：cnt */
    @Select("SELECT DATE(created_at) AS day, COUNT(*) AS cnt FROM user WHERE created_at >= #{start} GROUP BY DATE(created_at)")
    List<Map<String, Object>> newUserDaily(LocalDateTime start);

    /** 近 N 天活跃用户按日（写日记 ∪ 建会话，去重）：ucnt */
    @Select("SELECT d AS day, COUNT(DISTINCT u) AS ucnt FROM (" +
            "SELECT DATE(created_at) AS d, user_id AS u FROM emotion_diary WHERE created_at >= #{start} " +
            "UNION ALL " +
            "SELECT DATE(started_at) AS d, user_id AS u FROM consultation_session WHERE started_at >= #{start}" +
            ") t GROUP BY d")
    List<Map<String, Object>> activeDaily(LocalDateTime start);

    /** 近 N 天活跃用户总数（写日记 ∪ 建会话，去重）：cnt */
    @Select("SELECT COUNT(DISTINCT u) AS cnt FROM (" +
            "SELECT user_id AS u FROM emotion_diary WHERE created_at >= #{start} " +
            "UNION ALL " +
            "SELECT user_id AS u FROM consultation_session WHERE started_at >= #{start}" +
            ") t")
    Map<String, Object> activeTotal(LocalDateTime start);

    /** 日记全量 count 与 mood_score 均值：cnt、avgscore */
    @Select("SELECT COUNT(*) AS cnt, AVG(mood_score) AS avgscore FROM emotion_diary")
    Map<String, Object> diaryOverview();

    /** 各含消息会话的时长分钟差（可含负值，Java 侧处理） */
    @Select("SELECT TIMESTAMPDIFF(MINUTE, s.started_at, lm.last_t) AS mins " +
            "FROM consultation_session s INNER JOIN (" +
            "SELECT session_id, MAX(created_at) AS last_t FROM consultation_message GROUP BY session_id" +
            ") lm ON lm.session_id = s.id " +
            "WHERE s.started_at IS NOT NULL AND lm.last_t IS NOT NULL")
    List<Map<String, Object>> sessionMinutes();
}
