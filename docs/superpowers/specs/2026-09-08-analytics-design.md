# 数据分析看板设计规格（GET /api/data-analytics/overview）

> 日期：2026-09-08
> 范围：`backendCode/ai-project`（只读统计；零建表、零前端改动）
> 关联：`docs/接口缺口清单.md`（缺口 #15，最后一项）、`docs/前端调用契约报告.md` §七

## 1. 背景与目标

后台 dashboard.vue 仅依赖 `GET /api/data-analytics/overview` 一个接口（已 404）。补齐后前端 22 个调用接口全部闭环。接口为**管理员专用**只读聚合统计。

## 2. 已确认决策

- **时间窗口**：近 7 天（含今天）按日；`date` 形如 `MM-dd`，零填充、升序；三趋势数组恒非 null。
- 口径见 §3（写入 CLAUDE/规格，后续可调整）。

## 3. 返回结构与指标口径

```
DataAnalyticsOverviewVO
├── systemOverview{totalUsers, activeUsers, totalDiaries, todayNewDiaries,
│                   totalSessions, todayNewSessions, avgMoodScore}
├── emotionTrend[]      {date, avgMoodScore, recordCount}
├── consultationStats{totalSessions, avgDurationMinutes,
│                     dailyTrend[]{date, sessionCount, userCount}}
└── userActivity[]      {date, activeUsers, newUsers, diaryUsers, consultationUsers}
```

| 指标 | 口径 |
|---|---|
| totalUsers / totalDiaries / totalSessions | 全量 count |
| todayNewDiaries / todayNewSessions | 今日 0 点起新增日记/会话 |
| avgMoodScore | 全部日记 mood_score(1-10) 均值，保留 1 位小数（前端按 /10 展示） |
| activeUsers（卡片） | 近 7 天建会话 ∪ 写日记去重用户数 |
| emotionTrend | 当日：recordCount=日记数；avgMoodScore=日记评分均值（无日记当日为 null，图表断线）；window 7d 升序 |
| consultationStats.avgDurationMinutes | 平均会话时长 = 均值(每会话 started_at→最后消息时间 分钟差，仅含消息会话)，四舍五入整数；无则 0 |
| consultationStats.dailyTrend | 当日创建会话数 / 去重建会话用户数 |
| userActivity | 当日：activeUsers=建会话∪写日记去重、newUsers=注册、diaryUsers=写日记者、consultationUsers=建会话者 |

## 4. 实现要点

- 新增 `mapper/AnalyticsMapper`（原生 `@Select` 聚合，不 extends BaseMapper，`@MapperScan` 覆盖注册）：日记/会话/注册/活跃按日 4 组 + 日记总览 + 活跃 7 天总数 + 会话时长分钟列表（join 最后消息）。
- SQL 列别名全小写（day/cnt/avgscore/diaryusers/ucnt/mins）规避大小写歧义；Java 统一 `(Number)`/`BigDecimal`/`Date` 转换。
- `AnalyticsService`/Impl 组装：`LocalDate.now().minusDays(6)` 起 7 天 → `LinkedHashMap<LocalDate, ...>` 零填充 → `MM-dd` 标签；`AuthzTool.requireAdmin` 守门。
- VO 单文件 `DataAnalyticsOverviewVO` 内含嵌套静态类（SystemOverviewVO/EmotionTrendItemVO/ConsultationStatsVO/ConsultationDailyItemVO/UserActivityItemVO）。
- 复用 Result 信封；错误码不新增；日期运算走 Java（无时区 SQL 函数差异风险）。

## 5. 文件改动清单

| 操作 | 文件 |
|---|---|
| 新增 | `mapper/AnalyticsMapper.java`、`dto/response/DataAnalyticsOverviewVO.java`、`service/AnalyticsService(.Impl)`、`controller/DataAnalyticsController.java` |
| 修改 | `CLAUDE.md`、`docs/接口缺口清单.md`（#15 ✅、计数更新为 0 剩余） |
| 新增 | 本规格 + `docs/superpowers/plans/2026-09-08-analytics.md` |

## 6. 验证

- 编译 + 既有 11 个纯 JVM 单测回归；
- 可选冒烟（DB 有少量数据）：核对总数/今日/近 7 天各口径、无日记日 avgMoodScore=null、三个数组长度 7 且升序、非管理员返回 A0301；
- 不做：图表联动、导出、多窗口切换、知识库阅读统计。
