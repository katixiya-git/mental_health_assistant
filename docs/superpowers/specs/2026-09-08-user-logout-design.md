# POST /api/user/logout 设计规格（内存 Token 黑名单）

> 日期：2026-09-08
> 范围：`backendCode/ai-project` + `frontendCode/ai-vue`（仅登出链路）
> 关联文档：`docs/接口缺口清单.md`（缺口 #16）、`docs/前端调用契约报告.md`

## 1. 背景与目标

前端两处登出入口（`Navbar.vue:45`、`FrontendLayout.vue:45`）调用 `POST /user/logout`，当前后端未实现（404）导致 `logout().then(...)` 不执行，**点退出无任何效果**。目标：补齐该接口，使登出真实生效（服务端使当前 JWT 失效），并保证登出在任何情况下都能清理前端本地状态。

## 2. 关键决策

- **检测结论**：本机未安装 Redis（无命令/服务/6379 监听/常见安装目录/无 Docker）→ 不采用 Redis 方案。
- **采用方案 B：内存 Token 黑名单**（`ConcurrentHashMap`）。单实例有效、重启失效，无新依赖，符合"最小、独立"范围。
- 方案 C（Redis 黑名单）记录为后续升级路径：安装 Redis 后，将 `TokenBlacklist` 内部实现替换为 `StringRedisTemplate`（SETEX + EXISTS），调用方与过滤器逻辑不变。

## 3. 数据流

1. 前端（已登录，localStorage 有 token）点退出 → axios `POST /api/user/logout`，请求头小写 `token`。
2. `JwtAuthenticationFilter`（非白名单路径）解析校验 token：
   - 有效：先查黑名单——**命中 → HTTP 401 + `Result`(`A0230` TOKEN_BLOCKED)**；未命中 → 把原始 token 写入 request attribute（`RAW_TOKEN_ATTR`）供 controller 复用，放行。
   - 无效/过期：401（现状不变）。
3. `UserController.logout()`：取 attribute 中的 token → `UserService.logout(token)` → 返回 `Result.ok()`（幂等，无 token 也返回成功）。
4. `UserServiceImpl.logout`：`parseToken` 取 `exp` → `TokenBlacklist.add(token, exp毫秒)`；token 无效/过期则忽略（无需拉黑）。
5. 前端 `.finally`：无论接口成败，清理 `localStorage` 并跳 `/auth/login`。

## 4. 契约

- `POST /api/user/logout`：需要有效 JWT（**不加入** `SecurityConstants.PUBLIC_URLS` 白名单）；无请求体；响应 `Result`（`code:"200"`），data 为 null。
- 错误码复用既有 `ResultCode.TOKEN_BLOCKED("A0230")`，**不新增错误码**。
- 幂等性：重复 logout（token 已被拉黑/过期）→ 过滤器 401；前端 `finally` 兜底，行为可接受。

## 5. 边界与取舍

- 黑名单随应用重启清空（无持久化）；重启后旧 token"复活"属已知取舍，写入文档与代码注释。
- 无定时任务：条目在 `contains()` 时惰性清理，且条目本身到 JWT exp 即失效，不会无限增长。
- token 过期后再 logout → 401（过滤器拦截），前端本地清理兜底，服务端无残留。
- 同一秒内为同一用户签发的两个相同 JWT 视为同一 token（拉黑其一即失效）——可接受。

## 6. 文件改动清单

| 操作 | 文件 |
|---|---|
| 新增 | `backendCode/ai-project/src/main/java/com/ai/aiproject/config/TokenBlacklist.java` |
| 新增 | `backendCode/ai-project/src/test/java/com/ai/aiproject/config/TokenBlacklistTest.java` |
| 修改 | `backendCode/ai-project/src/main/java/com/ai/aiproject/config/JwtAuthenticationFilter.java`（查黑名单 + 透传原始 token） |
| 修改 | `backendCode/ai-project/src/main/java/com/ai/aiproject/config/SecurityConfig.java`（过滤器 Bean 构造参数 +1） |
| 修改 | `backendCode/ai-project/src/main/java/com/ai/aiproject/service/UserService.java`（+`logout`） |
| 修改 | `backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/UserServiceImpl.java`（实现 + 注入 TokenBlacklist） |
| 修改 | `backendCode/ai-project/src/main/java/com/ai/aiproject/controller/UserController.java`（+`POST /logout`） |
| 修改 | `frontendCode/ai-vue/src/components/Navbar.vue`、`FrontendLayout.vue`（登出 `.then`→`.finally`） |
| 修改 | `backendCode/ai-project/CLAUDE.md`、`docs/接口缺口清单.md`（状态标注；顺带修正日期） |

## 7. 测试与验收

- 单元：`TokenBlacklistTest`（add/contains/未知 token/过期惰性清理），纯 JVM、不依赖 Spring/DB。
- 构建：`mvn -s E:\Ai-Code\DeepSeek\.mvn-settings.xml test -Dtest=TokenBlacklistTest`（同时编译全部源码）；前端 `npm run build`（如执行）。
- 手动链路（需本地 MySQL 与可用的 DASHSCOPE_KEY 环境，非本次必跑）：
  1. `POST /api/user/login` 取 token
  2. `POST /api/user/logout`（带 token）→ `code:"200"`
  3. 同 token 再调 `GET /api/user/current` → HTTP 401、`code:"A0230"`（黑名单生效）
  4. 前端：退出按钮可跳登录页；token 过期场景也能退出

## 8. 不做（YAGNI / Out of scope）

- 不加 Redis 依赖、不加 Spring Data Redis 配置。
- 不做 refresh token、记住登录、多端互踢、黑名单持久化。
- 不改 SecurityConfig 白名单；不改 Result/ResultCode/GlobalExceptionHandler。
