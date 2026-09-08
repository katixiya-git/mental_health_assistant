# CLAUDE.md

本文件为 Claude Code（claude.ai/code）在本仓库工作时提供指导。

## 项目概览

「宁渡课堂」心理健康助手后端，前后端分离，核心功能：情绪日记、AI 心理咨询、知识科普、用户管理、数据分析。

**当前状态**：项目骨架 + 用户模块已完成（登录、注册）。包根 `com.ai.aiproject`，入口 `AiProjectApplication.java`（`@SpringBootApplication` + `@MapperScan("com.ai.aiproject.mapper")`）。

### 实际技术栈（已落地）
- Spring Boot 3.4.1（parent），**Java 17**（为兼容 spring-ai 1.0.0-M5 从 4.1.1 降级而来）
- `mybatis-plus-spring-boot3-starter:3.5.16`（**Spring Boot 3 专用 starter**）
- `spring-boot-starter-security`（Spring Security 6.x）+ `com.auth0:java-jwt:4.4.0`（auth0 API，非 jjwt）
- `spring-boot-starter-web` + `spring-boot-starter-validation` + `mysql-connector-j`（runtime）+ Lombok
- `spring-ai-openai-spring-boot-starter:1.0.0-M5`：Boot 3.4.1 下兼容，已配 `spring.ai.openai.*` 接入阿里云百炼（api-key 从环境变量 `DASHSCOPE_API_KEY` 读取）
- 配置文件 `application.yaml`：`server.port=8080`、MySQL `mental_health_assistant`（root/123456）、`jwt.*`、`spring.ai.openai.*` 已配
- Maven Wrapper 元数据存在（`.mvn/wrapper/`），但**没有 `mvnw` 脚本**——用系统 Maven（`mvn`）

## 常用命令

```bash
mvn clean package          # 构建 + 运行测试，产出 boot jar
mvn spring-boot:run        # 本地启动应用（端口 8080）
mvn test                   # 运行全部测试
mvn test -Dtest=AiProjectApplicationTests   # 运行单个测试类
```

## 已实现接口

| 模块 | 接口 | 状态 |
|------|------|------|
| 用户 | `POST /api/user/login` | ✅ 登录：用户名或邮箱查询 + BCrypt 校验 + 状态校验 + 生成 JWT |
| 用户 | `POST /api/user/add` | ✅ 注册：密码一致性/用户名/邮箱/手机号唯一性/userType 校验 + BCrypt 加密入库，返回用户详情（不自动登录） |
| 用户 | `GET /api/user/current` | ✅ 获取当前登录用户信息（需 JWT，过滤器认证后从 SecurityContext 取 userId） |
| 用户 | `POST /api/user/logout` | ✅ 登出：需登录态；将当前 JWT 加入内存黑名单（config/TokenBlacklist，至 token 过期失效，重启清空）；前端登出已改 finally 兜底 |
| 咨询 | `POST /api/psychological-chat/session/start` | ✅ 创建会话：写 consultation_session（含 user_id）+ consultation_message（首条消息，无 user_id）；标题缺省「未声明标题」；返回 StreamChatSession |
| 咨询 | `POST /api/psychological-chat/stream` | ✅ 流式对话：SSE（text/event-stream）推送 qwen-plus 回复，正常 chunk `data={"code":"200","data":{"content":"..."}}`（event:message），结束 `event:done`（data 非空，前端 `if(!raw) return` 需非空），错误 `event:error`（data 为 `{code,message}`，前端取 payload.message）；会话归属校验；持久化用户消息(sender_type=1)与 AI 回复(sender_type=2, ai_model=qwen-plus)；**会话记忆**：DbChatMemory 从 DB 读历史（最近 20 条）拼多轮 Prompt |
| 咨询 | `GET /api/psychological-chat/sessions/{sessionId}/messages` | ✅ 获取会话消息：归属校验；返回 ConsultationMessageResponseDTO 列表（按创建时间升序，含 senderTypeDesc/messageTypeDesc/contentLength） |
| 其余 | 情绪日记/知识库/文件/分析 | ❌ 未实现 |

## 目录结构（当前）

```
src/main/java/com/ai/aiproject/
├── AiProjectApplication.java      # 启动类 + @MapperScan
├── controller/UserController.java # /api/user/login、/add、/current
├── controller/SessionController.java # /api/psychological-chat/session/start
├── service/UserService.java + service/Impl/UserServiceImpl.java
├── service/SessionService.java + service/Impl/SessionServiceImpl.java
├── mapper/UserMapper.java、ConsultationSessionMapper.java、ConsultationMessageMapper.java  # extends BaseMapper
├── entity/User.java、ConsultationSession.java、ConsultationMessage.java  # MyBatis Plus 注解
├── dto/command/                   # UserLoginCommandDTO、UserRegisterCommandDTO
├── dto/ConsultationSessionCreateDTO.java、ConsultationStreamDTO.java
├── dto/response/                  # UserLoginResponseDTO、StructOutPutResponseDTO（StreamChatSession record）
├── enums/                         # ResultCode、UserType、UserStatus
├── common/                        # Result.java、GlobalExceptionHandler.java、SecurityConstants.java（白名单）
├── config/                        # JwtConfig、SecurityConfig、JwtAuthenticationFilter、DbChatMemory（基于 DB 的 ChatMemory，会话记忆）、TokenBlacklist（内存 JWT 黑名单）
├── Exception/BusinessException.java   # 注意：包名大写 E
└── Utils/                             # 注意：包名大写 U
    ├── UserConvertTool.java           # entity↔DTO 转换工具
    ├── JwtTool.java                   # JWT 生成/解析工具
    ├── ResponseWriteTool.java         # 过滤器认证失败响应写入工具（HTTP 401 + Result JSON）
    └── PromptManage.java              # AI 系统提示词（心理疏导）
```

## 分层与代码约定

- 分层：`controller` → `service`（接口 + `Impl/` 实现）→ mapper/entity
- DTO 分包：`command/`（创建/更新）、`query/`（查询，未建）、`response/`（响应）
- 实体用 MyBatis Plus 注解（`@TableName`/`@TableField` 蛇形映射），DTO/实体用 Lombok
- 统一响应 `common/Result.java`（`ok()`/`ok(data)`/`error()`/`error(code,msg,data)`）+ `enums/ResultCode.java`
- 业务异常 `Exception/BusinessException`（带 code 构造器）+ `common/GlobalExceptionHandler`（已配 `@ExceptionHandler`）
- Service 方法直接返回 DTO，controller 用 `Result.ok(...)` 包装（login 返回 `UserLoginResponseDTO`，register 返回 `UserDetailResponseDTO`）
- 实体→DTO 转换集中在 `Utils/UserConvertTool`（静态方法）

## 安全与认证（当前）

- `config/SecurityConfig`：`PasswordEncoder` = `BCryptPasswordEncoder`；`SecurityFilterChain` 放行白名单（`common/SecurityConstants.PUBLIC_URLS`：`"/"`、`"/api/test"`、`"/api/user/login"`、`"/api/user/add"`、`"/error"`），其余 `authenticated()`；STATELESS + 禁用 csrf/formLogin/httpBasic；`addFilterBefore` 接入 `JwtAuthenticationFilter`
- `config/JwtAuthenticationFilter`（`OncePerRequestFilter`）：白名单路径跳过；**优先读 `token` 头（前端 axios/SSE 实际使用，JWT 原样）**，无则回退 `jwt.header`（Authorization）并去掉 `jwt.token-prefix`（`"Bearer "`）前缀，经 `JwtTool.parseToken` 验证；成功把 userId 写入 `SecurityContext`；无 token/无效/过期返回 HTTP 401 + Result JSON（`Utils/ResponseWriteTool`，复用 `ResultCode`：UNAUTHORIZED / TOKEN_INVALID / TOKEN_EXPIRED）
- 业务侧取当前用户：`UserServiceImpl.getCurrentUserInfo()` 从 `SecurityContextHolder` 取 userId 再查库，转换经 `UserConvertTool.entityToDetailResponse`
- `Utils/JwtTool`：auth0 JWT，`generateToken(User)`（claims: userId/username/userType，过期 = `jwt.expiration` 24h）+ `parseToken(String)`

## 错误码占用（enums/ResultCode.java，6000 段已满）

`BUSINESS_ERROR(6000)`、`ACCOUNT_SAME(6001 用户名已存在)`、`USER_NOT_EXIST(6002)`、`PASSWORD_ERROR(6003)`、`ACCOUNT_DISABLED(6004)`、`EMAIL_EXIST(6005)`、`PHONE_EXIST(6006)`、`PASSWORD_MISMATCH(6007)`

## 数据库要点

- 库 `mental_health_assistant`，表 `user`：`username`/`email`/`phone` 三列各 UNIQUE（注册时代码层先查重，DB 唯一键兜底）
- 密码一律 BCrypt 存储（`$2a$`），登录用 `passwordEncoder.matches(...)`

## 目标架构（后续待落地，以 `.CLAUDE/宁渡课堂-后端技术文档.md` 为准）

- **AI 集成**：Spring AI（`spring-ai-openai-spring-boot-starter:1.0.0-M5`，已接入阿里云百炼），模型 `qwen-plus`，走 `/v1/chat/completions`
- **缓存**：Spring Data Redis（依赖未引入；token 黑名单已用内存实现 config/TokenBlacklist，Redis 缓存/集群共享黑名单待落地）
- 其他：Spring AOP、Spring Mail、hutool
- 前端已使用但未实现的接口见技术文档第 6 节（咨询/日记/知识库/文件/分析），路径以 `controller` 实际 `@RequestMapping` 为准（注意 `/api` 前缀）
- **待补接口的逐条缺口清单（16 项，含参数/返回结构/决策点/落地顺序）见仓库根 `docs/接口缺口清单.md`，行号级前端契约证据见同目录 `前端调用契约报告.md`；开始补模块前须先制定 Plan**

### 配置要点（参考）
- **JWT**（已落地）：`jwt.secret`、`expiration` 24h、`refresh-expiration` 7天、header `Authorization`、prefix `"Bearer "`
- **AI**（已接入百炼）：`spring.ai.openai.*`（api-key=`${DASHSCOPE_API_KEY}` 环境变量、base-url=`https://dashscope.aliyuncs.com/compatible-mode`、model=qwen-plus）

## 实体关键字段（参考）
- `User`：username（3-50，字母数字下划线）、email、phone（`^1[3-9]\d{9}$`）、password（6-255）、`user_type`（1 普通 / 2 管理员）、`status`（0 禁用 / 1 正常）；转换用 `UserType`/`UserStatus` 枚举 + `Utils/UserConvertTool`
- `ConsultationSession`：`user_id`、`session_title`、`started_at`、`last_emotion_analysis`（JSON）
- `ConsultationMessage`：`session_id`、`sender_type`（1 用户 / 2 AI）、`message_type`（1 文本）、`emotion_tag`、`ai_model`
