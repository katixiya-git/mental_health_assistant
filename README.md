# 心理健康AI助手（宁渡课堂）

一个前后端分离的心理健康 Web 应用：AI 心理咨询（流式对话 + 会话情绪分析）、情绪日记（AI 情绪分析）、心理健康知识库（OSS 封面上传）、用户/管理员权限与数据分析看板。本仓库为主代码（重构自课程示例 `ai-spingboot`，参考教学微服务工程 `tjxt` 未包含在本仓库）。

> ⚠️ 项目用途：学习 / 课程演示。AI 输出与建议不构成医疗诊断，严重心理困扰请及时寻求专业帮助。

## 功能模块与接口

前端共调用 22 个业务接口（`/api/*`），已全部实现：

| 模块 | 能力 | 主要接口 |
|---|---|---|
| 用户 | 注册 / 登录 / 当前用户 / 登出（JWT + 内存黑名单） | `POST /api/user/add|login|logout`、`GET /api/user/current` |
| AI 心理咨询 | 会话创建、SSE 流式对话（qwen-plus、DB 多轮记忆）、消息历史、会话分页/删除、会话情绪分析（增量缓存） | `/api/psychological-chat/**` |
| 情绪日记 | 记日记（评分/情绪/睡眠/压力）+ best-effort AI 情绪分析；管理端分页/详情/删除 | `/api/emotion-diary/**` |
| 知识库 | 分类树、文章分页（管理员/用户双模式）、详情（阅读数）、CRUD/上下线 | `/api/knowledge/**` |
| 文件 | 图片上传到阿里云 OSS（公共读） | `POST /api/file/upload` |
| 数据分析 | 管理端看板总览（近 7 天聚合） | `GET /api/data-analytics/overview` |

接口明细与契约见 `docs/接口缺口清单.md`、`docs/前端调用契约报告.md`。

## 技术栈

- **后端** `backendCode/ai-project`：Spring Boot 3.4.1 / Java 17、MyBatis-Plus 3.5.16（含分页拦截器）、Spring Security 6 + auth0 JWT、Spring AI（阿里云百炼 `qwen-plus`）、阿里云 OSS SDK、Hutool
- **前端** `frontendCode/ai-vue`：Vue 3 + Vite + Element Plus + ECharts + Pinia + wangeditor，`/api` 由 Vite 代理到 `localhost:8080`
- **数据库**：MySQL `mental_health_assistant`

## 目录结构（概要）

```
backendCode/ai-project        # 后端主工程（com.ai.aiproject）
frontendCode/ai-vue           # 前端工程
docs/                         # 需求/契约/缺口清单/SQL/设计与实现计划
  ├── sql/                    # 建表 DDL（emotion_diary、knowledge_* 需手动执行）
  └── superpowers/{specs,plans}/  # 各模块设计规格与实现计划
CLAUDE.md（ai-project 内）     # 后端开发指引/已实现接口清单
```

## 本地启动

### 1. 准备

- MySQL 建库并执行建表脚本（幂等，可重复执行）：
  ```sql
  -- 在 mysql 客户端/IDE 中执行
  mysql -uroot -p123456 mental_health_assistant < docs/sql/2026-09-08-emotion-diary.sql
  mysql -uroot -p123456 mental_health_assistant < docs/sql/2026-09-08-knowledge.sql
  ```
- 设置环境变量（密钥请用密码管理器保管，勿提交仓库）：

  | 变量 | 说明 | 是否必填 |
  |---|---|---|
  | `DASHSCOPE_API_KEY` | 阿里云百炼 API-KEY（AI 对话/情绪分析） | 需要 AI 功能时 |
  | `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | RAM AccessKey（封面上传） | 需要上传时 |
  | `DB_HOST` / `DB_PORT` / `DB_USERNAME` / `DB_PASSWORD` | 数据库连接（均有本地默认值） | 否 |
  | `JWT_SECRET` | JWT 签名密钥（有本地默认值） | 否 |

### 2. 启动后端（端口 8080）

```bash
cd backendCode/ai-project
mvn -s "$(pwd)/../../.mvn-settings.xml" spring-boot:run   # 无 mvnw，使用系统 Maven
# 或：mvn -s <你的 .mvn-settings.xml> spring-boot:run
```

### 3. 启动前端（端口 5173）

```bash
cd frontendCode/ai-vue
npm install
npm run dev
```

浏览器访问 http://localhost:5173，注册普通账号体验前台；后台（`/back`）需管理员（`user_type = 2`）账号。

## 测试

```bash
cd backendCode/ai-project
mvn test   # 12 个用例：纯 JVM 单测 + @SpringBootTest contextLoads
```

> `pom.xml` 已为 surefire 配置 `-Djdk.attach.allowAttachSelf=true`（否则部分 JDK 下 Mockito/ByteBuddy self-attach 失败）。

## 文档索引

- `docs/接口缺口清单.md` — 前端 22 接口 ↔ 后端实现状态（已全部闭环）
- `docs/前端调用契约报告.md` — 逐接口前端调用契约（文件+行号）
- `docs/superpowers/specs/`、`docs/superpowers/plans/` — 各功能模块的设计规格与实现计划
- `backendCode/ai-project/CLAUDE.md` — 后端架构/约定/接口清单速查

## 安全说明

- 仓库不含任何真实密钥（百炼/OSS/数据库均走环境变量或本地默认值）。
- 生产或公开演示前请自行设置 `JWT_SECRET`、轮换曾暴露过的密钥，并按需收紧 OSS 权限。

## License

[MIT](./LICENSE)
