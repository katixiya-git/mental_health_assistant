# 心理健康AI助手

> 一个功能完整、适合学习与二次开发的前后端分离 **AI 心理健康 Web 应用**。
> 面向用户提供 **AI 心理咨询（流式对话）**、**情绪日记与 AI 情绪分析**、**心理健康科普知识库**；面向管理员提供数据看板与内容/数据管理后台。

---

## ✨ 技术栈

| 层面 | 技术 |
|---|---|
| 后端 | Spring Boot 3.4.1 · MyBatis-Plus 3.5.16 · Java 17 · Spring AI（阿里云百炼 qwen-plus）· 阿里云 OSS |
| 数据库 | MySQL 8（mental_health_assistant） |
| 安全 | Spring Security + JWT（auth0）· BCrypt 密码哈希 · token 内存黑名单登出 · 角色/归属双重鉴权 |
| 前端 | Vue 3（`<script setup>`）· Vite · Element Plus · Pinia · vue-router · axios · ECharts · wangeditor（富文本） |
| 部署 | 前后端分离：Vite dev 代理（`/api` → 8080）；接口统一 `/api` 前缀 + `Result` 信封 |

## 🧩 功能特性

**用户端（普通用户）**
- **AI 心理咨询**：SSE 流式对话（qwen-plus），基于数据库的多轮会话记忆；会话历史侧栏（新建/续聊/删除）；对话结束后自动刷新「情绪花园」情绪分析
- **情绪日记**：评分（1-10）/主要情绪/正文/睡眠与压力记录；有正文时自动调用 AI 做情绪分析，失败自动降级不影响保存
- **心理健康科普**：分类浏览文章列表（按发布时间/阅读量排序），文章详情含富文本内容与标签，访问自动累计阅读量
- 账号：注册（用户名/邮箱/手机唯一性校验）、登录、登出

**管理端（管理员）**
- **数据看板**：总览统计卡（用户/日记/会话/平均情绪）+ 近 7 日情绪趋势、咨询活动、用户活跃度 ECharts 图表
- **知识文章管理**：富文本编辑器 + 封面图上传（OSS）+ 草稿/发布/下线状态管理，分类树维护
- **咨询记录**：分页查看全部用户会话、对话消息详情
- **情绪日志**：按用户/评分范围筛选，详情含 AI 情绪分析结果，支持删除

## 🔒 亮点与健壮性要点（本仓库实际实现）

- **AI 能力**：对话与两类情绪分析（日记/会话）均接阿里云百炼 qwen-plus；结构化 JSON 规范化后落库，AI 异常/缺 Key 自动降级，不阻塞核心流程；会话情绪分析采用“有新消息才重算”的增量缓存
- **流式对话**：SSE 推送增量内容，React 管线空 chunk 过滤 + 异步分发放行，断流自动兜底
- **认证与鉴权**：JWT（stateless）+ 自定义过滤器；登出将 token 加入内存黑名单真正失效；管理员接口服务层校验（A0301），越权与跨用户访问被拒绝
- **数据一致性**：文章阅读量用 `read_count = read_count + 1` 原子自增；删除会话级联清理消息；关键写操作事务化
- **上传安全**：OSS 封面上传做类型白名单（jpg/png/webp 等）+ 大小（≤5MB）限制 + UUID 重命名
- **工程约定**：接口统一 `Result{code,msg,data}` 信封；分页参数多别名兼容；实体→DTO 分层转换；纯静态工具自带单测

## 📁 目录结构

```
├── backendCode/ai-project        # 后端主工程（Spring Boot，com.ai.aiproject）
├── frontendCode/ai-vue           # 前端工程（Vue 3 + Vite）
├── docs/
│   ├── sql/                      # 建表 DDL（emotion_diary、knowledge_*，幂等）
│   └── superpowers/              # 各功能模块的设计规格与实现计划
└── README.md / LICENSE
```

## 🚀 快速开始

**环境**：JDK 17 · Maven 3.6+ · MySQL 8 · Node.js 18+

1. **初始化数据库**：创建库 `mental_health_assistant` 并执行建表脚本（幂等）：
   ```bash
   mysql -uroot -p123456 mental_health_assistant < docs/sql/2026-09-08-emotion-diary.sql
   mysql -uroot -p123456 mental_health_assistant < docs/sql/2026-09-08-knowledge.sql
   ```
2. **配置环境变量**（密钥不入库；均为可覆盖项）：

   | 变量 | 用途 |
   |---|---|
   | `DASHSCOPE_API_KEY` | 阿里云百炼 API-KEY（AI 对话与情绪分析） |
   | `OSS_ACCESS_KEY_ID` / `OSS_ACCESS_KEY_SECRET` | 阿里云 OSS AccessKey（文章封面上传） |
   | `DB_PASSWORD`（可选，默认 `123456`） | 数据库密码；`JWT_SECRET` 可选覆盖 JWT 密钥 |

3. **启动后端**（端口 8080）：
   ```bash
   cd backendCode/ai-project
   mvn spring-boot:run
   ```
4. **启动前端**（端口 5173）：
   ```bash
   cd frontendCode/ai-vue
   npm install && npm run dev
   ```
   访问 http://localhost:5173 —— 注册普通账号体验用户端；后台 `/back` 需管理员（`user_type=2`）。

> 心理健康提示：AI 回复与情绪分析仅供参考，不构成医疗诊断；严重心理困扰请及时寻求专业帮助。

## 🖼 界面预览

> 说明：可在仓库内新增 `示例/` 目录放入前后台页面截图，并按以下格式引用（与参考 README 风格一致）：
> `![前台](示例/前台-咨询.png)`

## 📄 License

[MIT](./LICENSE) · **Author: [katixiya]**
