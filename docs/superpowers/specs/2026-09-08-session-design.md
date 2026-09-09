# 咨询会话补充模块设计规格（分页列表 / 级联删除 / 增量情绪分析）

> 日期：2026-09-08
> 范围：`backendCode/ai-project`（零前端改动、零建表）
> 关联：`docs/接口缺口清单.md`（缺口 #1/#2/#3）、`docs/前端调用契约报告.md` §二/§三

## 1. 背景与目标

前端咨询页会话侧栏（列表/删除/情绪花园）与后台咨询记录页（分页列表/详情）共依赖 3 个缺失接口：`GET /psychological-chat/sessions`、`DELETE /psychological-chat/sessions/{sessionId}`、`GET /psychological-chat/session/{sessionId}/emotion`。补齐后咨询模块闭环。

## 2. 已确认决策

- **分页双参数兼容**：同一列表接口同时接收用户端 `pageNum/pageSize` 与管理端 `currentPage/size`（各自唯一使用方，服务端归一）。
- **删除级联（A）**：删会话时先删其全部 `consultation_message` 再删会话；`@Transactional`；幂等（会话不存在直接 ok）。
- **情绪分析增量策略（B）**：仅当最新消息时间晚于 `lastEmotionUpdatedAt` 才重算；否则直接返回已存 `lastEmotionAnalysis`。AI 失败时保留旧缓存，无缓存返回默认对象；失败不更新时间戳（下次有新消息再试）。**永不阻塞/报错**——保证情绪花园可用。
- 权限：读/删/情绪接口均「本人或管理员」，非所有者按「会话不存在或无权访问」处理（不泄露他人会话存在性）；管理员分页看全部、用户只看自己。
- `session_` 前缀解析复用 `SessionServiceImpl.parseSessionId` 兼容逻辑（数字/带前缀）。

## 3. 接口契约（相对 /api，需登录）

| # | 接口 | 行为 |
|---|---|---|
| 1 | `GET /psychological-chat/sessions` | 角色分流 + 双分页参数 + `started_at desc, id desc`；返回 MP `Page{records,total}`，行含 `id/sessionTitle/startedAt/lastMessageContent/lastMessageTime/messageCount/durationMinutes/userNickname`（user 补全；durationMinutes = max(0, 分钟差(lastMessageTime − startedAt))） |
| 2 | `DELETE /psychological-chat/sessions/{sessionId}` | 归属校验（owner/admin）→ 级联删消息 → 删会话；幂等 |
| 3 | `GET /psychological-chat/session/{sessionId}/emotion` | 归属校验 → 增量分析（策略 B）；返回 `SessionEmotionVO{primaryEmotion, emotionScore, isNegative, riskLevel, suggestion, improvementSuggestions[], riskDescription}` |

## 4. 实现要点

- 消息统计：逐会话 `selectCount` + 最新一条（`created_at desc, id desc LIMIT 1`），页 ≤10 成本可控。
- 情绪分析：最近 30 条消息带角色标签拼 prompt → 新常量 `PromptManage.SESSION_EMOTION_ANALYSIS_SYSTEM_PROMPT`（只输出 JSON，键与 VO 相同）；解析后数值夹取（score 0-100、risk 0-3）、数组兜底空、字符串缺省，再落库 `last_emotion_analysis`（JSON 字符串）并更新 `last_emotion_updated_at`。
- 默认对象（无消息/无缓存/失败）：`中性 / 50 / false / 0 / 情绪状态平稳 / [] / ""`（与前端初始值一致）。
- 复用：`AuthzTool.isAdmin`、分页拦截器、`Result` 信封；`SessionServiceImpl` 注入 `UserMapper`（用于列表昵称与管理分流）。
- 错误码全部复用；不新增表/列（`consultation_session` 两列已确认存在）。

## 5. 文件改动清单

| 操作 | 文件 |
|---|---|
| 新增 | `dto/query/SessionPageQueryDTO.java`、`dto/response/SessionPageItemVO.java`、`dto/response/SessionEmotionVO.java` |
| 修改 | `service/SessionService.java`、`service/Impl/SessionServiceImpl.java`（+3 方法及私有分析/解析/默认辅助）、`controller/SessionController.java`、`Utils/PromptManage.java` |
| 修改 | `CLAUDE.md`、`docs/接口缺口清单.md`（#1/#2/#3 ✅） |
| 新增 | 本规格 + `docs/superpowers/plans/2026-09-08-session.md` |

## 6. 验证

- 编译 + 既有 11 个纯 JVM 单测回归（逻辑强依赖 Spring/DB，不强行造 mock 单测）；
- 可选冒烟（DB + Key）：对话后列表可见且 messageCount/最后消息正确 → 两套分页参数各验一次 → emotion 首次分析、无新消息命中缓存、新消息触发重算 → 删除后消息表无残留；
- 不做：管理端筛选/批量删除/导出、游客访问、情绪分析异步化。

## 7. 已知限制

- AI 分析同步执行（量小可接受）；DASHSCOPE key 缺失/失败时接口仍返回缓存或默认对象，不影响对话与删除。
