# 实现计划：数据分析看板（GET /api/data-analytics/overview）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）跟踪进度。
> 仓库根：`E:\Ai-Code\DeepSeek\心理健康Ai助手`；后端命令在 `backendCode/ai-project`，Maven 一律 `mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" ...`。
> 环境注意：Mockito self-attach 不可用 → 回归只跑既有纯 JVM 单测；本任务验证以编译 + 回归为主，可选运行时冒烟需 DB 数据。

**目标：** 实现看板聚合接口 `GET /api/data-analytics/overview`（管理员），返回 systemOverview + emotionTrend(7d) + consultationStats(含 dailyTrend) + userActivity(7d)，补齐前端 22 个接口中最后一个。

**架构：** 原生 SQL 聚合集中在 `mapper/AnalyticsMapper`（@Select 返回 List<Map>）；`AnalyticsService` 负责近 7 天零填充组装；`AuthzTool.requireAdmin` 守门；VO 单文件嵌套静态类。

**技术栈：** Spring Boot 3.4.1 / MyBatis-Plus（@Select 原生）/ Java 17 / MySQL。

---

### 任务 0：规格与计划入库

- [ ] **步骤 1：提交（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "docs/superpowers/specs/2026-09-08-analytics-design.md" "docs/superpowers/plans/2026-09-08-analytics.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 数据分析看板设计规格与实现计划"
```

---

### 任务 1：AnalyticsMapper（原生聚合 SQL）

**文件：**
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/AnalyticsMapper.java`

- [ ] **步骤 1：Mapper（完整文件，别名全小写）**

```java
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
```

- [ ] **步骤 2：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=OssFileToolTest,TokenBlacklistTest,EmotionDiaryToolTest"`
预期：11/11 PASS（BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/AnalyticsMapper.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(analytics): 看板原生聚合 AnalyticsMapper"
```

---

### 任务 2：Overview VO + AnalyticsService

**文件：**
- 创建：`dto/response/DataAnalyticsOverviewVO.java`
- 创建：`service/AnalyticsService.java`、`service/Impl/AnalyticsServiceImpl.java`

- [ ] **步骤 1：DataAnalyticsOverviewVO（单文件含嵌套静态类）**

```java
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
```

- [ ] **步骤 2：Service 接口**

```java
package com.ai.aiproject.service;

import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO;

/**
 * 数据看板统计服务
 */
public interface AnalyticsService {

    /** 管理员看板总览（近 7 天按日 + 全量/今日口径） */
    DataAnalyticsOverviewVO getOverview();
}
```

- [ ] **步骤 3：ServiceImpl（完整文件）**

```java
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
import java.util.LinkedHashMap;
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
```

- [ ] **步骤 4：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=OssFileToolTest,TokenBlacklistTest,EmotionDiaryToolTest"`
预期：11/11 PASS（BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/DataAnalyticsOverviewVO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/AnalyticsService.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/AnalyticsServiceImpl.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(analytics): Overview VO 与 AnalyticsService（近7天零填充组装）"
```

---

### 任务 3：DataAnalyticsController

**文件：**
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/controller/DataAnalyticsController.java`

- [ ] **步骤 1：Controller（完整文件）**

```java
package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.response.DataAnalyticsOverviewVO;
import com.ai.aiproject.service.AnalyticsService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数据分析控制器（管理员）
 * <p>
 * GET /api/data-analytics/overview
 */
@RestController
@RequestMapping("/api/data-analytics")
public class DataAnalyticsController {

    @Resource
    private AnalyticsService analyticsService;

    @GetMapping("/overview")
    public Result<DataAnalyticsOverviewVO> overview() {
        return Result.ok(analyticsService.getOverview());
    }
}
```

- [ ] **步骤 2：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=OssFileToolTest,TokenBlacklistTest,EmotionDiaryToolTest"`
预期：11/11 PASS（BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/controller/DataAnalyticsController.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(analytics): DataAnalyticsController GET /api/data-analytics/overview"
```

---

### 任务 4：文档更新与整体回归

**文件：**
- 修改：`backendCode/ai-project/CLAUDE.md`
- 修改：`docs/接口缺口清单.md`

- [ ] **步骤 1：CLAUDE.md**

①「已实现接口」表末行“| 其余 | 数据分析 | ❌ 未实现 |”替换为：

```markdown
| 分析 | `GET /api/data-analytics/overview` | ✅ 看板总览（管理员）：systemOverview 全量/今日/活跃口径 + 近7天 emotionTrend/consultationStats.dailyTrend/userActivity（零填充、MM-dd）；口径见规格文档 |
```

（此时已无“未实现”业务接口行。）

② 目录结构树尾部附近追加：

```text
├── mapper/AnalyticsMapper.java（原生 @Select 聚合）、service/AnalyticsService(.Impl)（近7天零填充组装）
├── controller/DataAnalyticsController.java   # /api/data-analytics/overview
```

- [ ] **步骤 2：docs/接口缺口清单.md**

① §2.5 表格（#15）表头加“状态”列并标注 `✅ 2026-09-08 已实现（管理员专用，近7天聚合）`。
② 头部“结论基准”改为：`前端实际调用 22 个接口已全部实现，16 项缺口全部 ✅（2026-09-08）。`
③ §2 标题更新为 `## 2. 缺口总表（16 项：已全部实现 ✅）`。
④ §4 落地顺序补第 5 条完成注记：`5. … — ✅ 已于 2026-09-08 完成`。

- [ ] **步骤 3：整体回归**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=OssFileToolTest,TokenBlacklistTest,EmotionDiaryToolTest"`
预期：PASS（11/11，BUILD SUCCESS）

（可选冒烟，需 DB 数据 + 管理员 token：GET /api/data-analytics/overview → 四块字段齐全、趋势数组长度 7、无日记日 avgMoodScore=null；普通用户 token → A0301。）

- [ ] **步骤 4：Commit**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/CLAUDE.md" "docs/接口缺口清单.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 标注数据分析接口已实现，缺口清零"
```
