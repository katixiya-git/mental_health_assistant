package com.ai.aiproject.service.Impl;

import com.ai.aiproject.Utils.AuthzTool;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO.ConsultationDailyItemVO;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO.ConsultationStatsVO;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO.EmotionTrendItemVO;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO.SystemOverviewVO;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO.UserActivityItemVO;
import com.ai.aiproject.entity.ConsultationSession;
import com.ai.aiproject.entity.EmotionDiary;
import com.ai.aiproject.mapper.AnalyticsMapper;
import com.ai.aiproject.mapper.ConsultationSessionMapper;
import com.ai.aiproject.mapper.EmotionDiaryMapper;
import com.ai.aiproject.mapper.UserMapper;
import com.ai.aiproject.service.AnalyticsService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AnalyticsServiceImpl implements AnalyticsService {

    private final AnalyticsMapper analyticsMapper;
    private final UserMapper userMapper;
    private final EmotionDiaryMapper emotionDiaryMapper;
    private final ConsultationSessionMapper consultationSessionMapper;

    @Override
    public DataAnalyticsOverviewVO getOverview() {
        AuthzTool.requireAdmin(userMapper);

        LocalDate today = LocalDate.now();
        LocalDate startDay = today.minusDays(6);
        LocalDateTime start = startDay.atStartOfDay();
        LocalDateTime todayStart = today.atStartOfDay();

        Map<LocalDate, Map<String, Object>> diaryByDay = toDayMap(analyticsMapper.diaryDaily(start));
        Map<LocalDate, Map<String, Object>> sessionByDay = toDayMap(analyticsMapper.sessionDaily(start));
        Map<LocalDate, Map<String, Object>> userByDay = toDayMap(analyticsMapper.newUserDaily(start));
        Map<LocalDate, Map<String, Object>> activeByDay = toDayMap(analyticsMapper.activeDaily(start));

        DataAnalyticsOverviewVO vo = new DataAnalyticsOverviewVO();

        // 1. 统计卡
        SystemOverviewVO sys = new SystemOverviewVO();
        sys.setTotalUsers(userMapper.selectCount(null));
        sys.setTotalDiaries(emotionDiaryMapper.selectCount(null));
        sys.setTotalSessions(consultationSessionMapper.selectCount(null));
        sys.setTodayNewDiaries(emotionDiaryMapper.selectCount(
                Wrappers.<EmotionDiary>lambdaQuery().ge(EmotionDiary::getCreatedAt, todayStart)));
        sys.setTodayNewSessions(consultationSessionMapper.selectCount(
                Wrappers.<ConsultationSession>lambdaQuery().ge(ConsultationSession::getStartedAt, todayStart)));
        Map<String, Object> overview = analyticsMapper.diaryOverview();
        sys.setAvgMoodScore(round1(toDouble(overview.get("avgscore"))));
        Map<String, Object> activeTotal = analyticsMapper.activeTotal(start);
        sys.setActiveUsers(toLong(activeTotal == null ? null : activeTotal.get("cnt")));
        vo.setSystemOverview(sys);

        // 2. 情绪趋势（近 7 天）
        List<EmotionTrendItemVO> emotionTrend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = startDay.plusDays(i);
            Map<String, Object> row = diaryByDay.get(day);
            EmotionTrendItemVO item = new EmotionTrendItemVO();
            item.setDate(fmtDay(day));
            int cnt = row == null ? 0 : toInt(row.get("cnt"));
            item.setRecordCount(cnt);
            item.setAvgMoodScore(cnt > 0 ? round1(toDouble(row.get("avgscore"))) : null);
            emotionTrend.add(item);
        }
        vo.setEmotionTrend(emotionTrend);

        // 3. 咨询统计
        ConsultationStatsVO stats = new ConsultationStatsVO();
        stats.setTotalSessions(sys.getTotalSessions());
        stats.setAvgDurationMinutes(avgSessionMinutes());
        List<ConsultationDailyItemVO> dailyTrend = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = startDay.plusDays(i);
            Map<String, Object> row = sessionByDay.get(day);
            ConsultationDailyItemVO item = new ConsultationDailyItemVO();
            item.setDate(fmtDay(day));
            item.setSessionCount(row == null ? 0 : toInt(row.get("cnt")));
            item.setUserCount(row == null ? 0 : toInt(row.get("ucnt")));
            dailyTrend.add(item);
        }
        stats.setDailyTrend(dailyTrend);
        vo.setConsultationStats(stats);

        // 4. 用户活跃（近 7 天）
        List<UserActivityItemVO> userActivity = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate day = startDay.plusDays(i);
            Map<String, Object> aRow = activeByDay.get(day);
            Map<String, Object> dRow = diaryByDay.get(day);
            Map<String, Object> sRow = sessionByDay.get(day);
            Map<String, Object> nRow = userByDay.get(day);
            UserActivityItemVO item = new UserActivityItemVO();
            item.setDate(fmtDay(day));
            item.setActiveUsers(aRow == null ? 0 : toInt(aRow.get("ucnt")));
            item.setNewUsers(nRow == null ? 0 : toInt(nRow.get("cnt")));
            item.setDiaryUsers(dRow == null ? 0 : toInt(dRow.get("diaryusers")));
            item.setConsultationUsers(sRow == null ? 0 : toInt(sRow.get("ucnt")));
            userActivity.add(item);
        }
        vo.setUserActivity(userActivity);
        return vo;
    }

    /** 平均会话时长（分钟，整数四舍五入） */
    private Integer avgSessionMinutes() {
        List<Map<String, Object>> rows = analyticsMapper.sessionMinutes();
        if (rows.isEmpty()) {
            return 0;
        }
        double sum = 0;
        for (Map<String, Object> row : rows) {
            sum += toLong(row.get("mins"));
        }
        return (int) Math.round(sum / rows.size());
    }

    private Map<LocalDate, Map<String, Object>> toDayMap(List<Map<String, Object>> rows) {
        Map<LocalDate, Map<String, Object>> map = new HashMap<>();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                LocalDate day = toLocalDate(row.get("day"));
                if (day != null) {
                    map.put(day, row);
                }
            }
        }
        return map;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate) {
            return (LocalDate) value;
        }
        if (value instanceof Date) {
            return ((Date) value).toLocalDate();
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).toInstant()
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private String fmtDay(LocalDate day) {
        return String.format("%02d-%02d", day.getMonthValue(), day.getDayOfMonth());
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private int toInt(Object value) {
        return value == null ? 0 : ((Number) value).intValue();
    }

    private double toDouble(Object value) {
        return value == null ? 0d : ((Number) value).doubleValue();
    }

    private Double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
