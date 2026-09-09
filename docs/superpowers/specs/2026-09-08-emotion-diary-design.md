# 情绪日记模块设计规格（emotion-diary）

> 日期：2026-09-08
> 范围：`backendCode/ai-project`（零前端改动）
> 关联：`docs/接口缺口清单.md`（缺口 #4/#5/#6）、`docs/前端调用契约报告.md` §四

## 1. 背景与目标

前端用户页（`emotionDiary.vue`）提交情绪日记、后台页（`emotional.vue`）分页查看/删除并展示 AI 情绪分析，三个接口后端均未实现（404）。目标：补齐 3 个接口，与前端契约严格一致。

## 2. 已确认决策

- **日记粒度**：同一用户同一天允许多条（不设唯一约束），趋势统计按日聚合（属数据分析模块）。
- **AI 情绪分析策略（B）**：保存日记时，仅当 `diaryContent` 或 `emotionTriggers` 非空才同步调用 qwen 分析，成功将**规范化 JSON 字符串**写入 `ai_emotion_analysis`；无正文或调用失败/超时 → 字段为 null，**不阻塞保存**。

## 3. 接口契约（相对 /api）

### POST /emotion-diary（登录用户）
- 请求体（JSON）：`diaryDate`(LocalDate, 必填)、`moodScore`(1-10, 必填)、`dominantEmotion`(选填，8 选 1：开心/平静/焦虑/悲伤/兴奋/疲惫/惊讶/困惑)、`emotionTriggers`(≤1000)、`diaryContent`(≤2000)、`sleepQuality`/`stressLevel`(1-5, 选填)。
- 校验：@Valid 负责必填/范围/长度；主要情绪白名单在 `EmotionDiaryTool` 校验（空放行，非法 → PARAM_INVALID）。
- 响应：`Result.ok()`（data=null）。记录 `user_id` = 当前 JWT 用户。

### GET /emotion-diary/admin/page（管理员）
- 参数：`current`(默认1)、`size`(默认10)、`userId`(选填)、`moodScreRange`(选填：`'1-3'|'4-6'|'7-10'`，**保留前端拼写**，服务端解析为 `mood_score between [low,high]`)。
- 返回：MyBatis-Plus `Page<EmotionDiaryAdminItemDTO>`（含 `records,total`，前端解构 `{records,total}`）；行字段：`id,userId,username,nickname,diaryDate,moodScore,dominantEmotion,sleepQuality,stressLevel,emotionTriggers,diaryContent,aiEmotionAnalysis(原始 JSON 串),createdAt,updatedAt`（username/nickname 由 user 表按 user_id 批量补全）。
- 排序：`diary_date DESC, id DESC`。
- 权限：非管理员 → BusinessException `A0301`。

### DELETE /emotion-diary/admin/{id}（管理员）
- 幂等：记录不存在也返回 `Result.ok()`。权限同上。

## 4. AI 情绪分析

- 提示词：`Utils/PromptManage.DIARY_EMOTION_ANALYSIS_SYSTEM_PROMPT`（要求只输出 JSON，键：`primaryEmotion/emotionScore(0-100)/isNegative/riskLevel(0-3)/suggestion/riskDescription/improvementSuggestions[]`）。
- 调用：`chatModel.call(Prompt)`；容错剥离可能的 ```json``` 包裹后 hutool 解析；**规范化**（score 夹取 0-100、risk 夹取 0-3、improvements 非数组给空数组）再落库，保证 JSON 键与前端 `JSON.parse` 消费一致。
- 整体 try-catch：任何异常 → ai_emotion_analysis=null，保存照常。

## 5. 权限模型

项目未启用方法级安全。Service 层 `requireAdmin()`：查当前 user（`SecurityContextTool.getCurrentUserId()` → `userMapper.selectById`），`userType != UserType.ADMIN(2)` → `BusinessException(A0301 访问未授权)`。

## 6. 表结构（新表 emotion_diary，DDL 需人工/按验收入库）

见 `docs/sql/2026-09-08-emotion-diary.sql`：`id PK AI`、`user_id`、`diary_date`、`mood_score`、`dominant_emotion`、`emotion_triggers`、`diary_content`、`sleep_quality`、`stress_level`、`ai_emotion_analysis TEXT`、`created_at`、`updated_at`，索引 `idx_user_date(user_id, diary_date)`，utf8mb4。

## 7. 基础设施附带改动

- 新增 `config/MybatisPlusConfig`：`PaginationInnerInterceptor(DbType.MYSQL)`（现状无分页插件，`selectPage` 依赖它生成 LIMIT）。对其他模块无副作用。

## 8. 文件改动清单

| 操作 | 文件 |
|---|---|
| 新增 | `docs/sql/2026-09-08-emotion-diary.sql` |
| 新增 | `config/MybatisPlusConfig.java`、`entity/EmotionDiary.java`、`mapper/EmotionDiaryMapper.java` |
| 新增 | `dto/command/EmotionDiaryAddCommandDTO.java`、`dto/query/EmotionDiaryAdminPageQueryDTO.java`、`dto/response/EmotionDiaryAdminItemDTO.java` |
| 新增 | `service/EmotionDiaryService.java`、`service/Impl/EmotionDiaryServiceImpl.java`、`controller/EmotionDiaryController.java` |
| 新增 | `Utils/EmotionDiaryTool.java`、`src/test/java/.../Utils/EmotionDiaryToolTest.java` |
| 修改 | `Utils/PromptManage.java`（+日记分析提示词） |
| 修改 | `CLAUDE.md`、`docs/接口缺口清单.md`（#4/#5/#6 状态） |
| 新增 | `docs/superpowers/specs/2026-09-08-emotion-diary-design.md`、`docs/superpowers/plans/2026-09-08-emotion-diary.md` |

## 9. 测试与验收

- 单元（纯 JVM，规避本环境 Mockito self-attach 不可用）：`EmotionDiaryToolTest`（范围解析/非法值/主要情绪白名单）。命令 `mvn -s ... test -Dtest=EmotionDiaryToolTest,TokenBlacklistTest`。
- 编译验证：同一命令编译全量主代码。
- 手动链路（需 MySQL 执行 DDL + DASHSCOPE_KEY，非本次必跑）：见计划任务 5。
- 不新增错误码（复用 PARAM_INVALID / ACCESS_UNAUTHORIZED / PARAM_ERROR）。

## 10. 不做（YAGNI / Out of scope）

- 用户端日记历史列表/图表（前端无调用）；情绪趋势统计（数据分析模块）；异步分析；Redis；管理员"查看即分析"（与前端详情直读行字段冲突）。
