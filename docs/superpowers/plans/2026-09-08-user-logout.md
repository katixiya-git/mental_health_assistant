# 实现计划：POST /api/user/logout（内存 Token 黑名单）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。
> 仓库根（git 与相对路径基准）：`E:\Ai-Code\DeepSeek\心理健康Ai助手`
> Maven 必须带设置文件：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" ...`（本地仓库 `.mvn-repo` 在工作区内）
> 全部命令在仓库根的 `backendCode/ai-project`（后端）或 `frontendCode/ai-vue`（前端）目录执行。

**目标：** 实现 `POST /api/user/logout`：校验登录态 → 将当前 JWT 加入内存黑名单（直至其 exp）→ 返回 `Result.ok()`；前端登出改为 `finally` 兜底清理本地状态。

**架构：** 新增 `config/TokenBlacklist`（`ConcurrentHashMap<token, expireAtMs>`，惰性过期清理）；`JwtAuthenticationFilter` 认证通过后先查黑名单（命中 401 `TOKEN_BLOCKED`），并把原始 token 透传到 request attribute；`UserController.logout` 经 `UserService.logout` 拉黑 token。

**技术栈：** Spring Boot 3.4.1 / Java 17、MyBatis-Plus（无关本次）、auth0 java-jwt、Spring Security；前端 Vue3 + Element Plus（axios）。

---

### 任务 1：TokenBlacklist 组件（TDD：先写失败测试）

**文件：**
- 测试：`backendCode/ai-project/src/test/java/com/ai/aiproject/config/TokenBlacklistTest.java`（新增）
- 实现：`backendCode/ai-project/src/main/java/com/ai/aiproject/config/TokenBlacklist.java`（新增）

- [ ] **步骤 1：编写失败的测试**

```java
package com.ai.aiproject.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenBlacklistTest {

    @Test
    void addThenContainsReturnsTrue() {
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.add("t1", System.currentTimeMillis() + 60_000);
        assertTrue(blacklist.contains("t1"));
    }

    @Test
    void unknownTokenReturnsFalse() {
        TokenBlacklist blacklist = new TokenBlacklist();
        assertFalse(blacklist.contains("not-added"));
    }

    @Test
    void expiredEntryEvictedAndReturnsFalse() {
        TokenBlacklist blacklist = new TokenBlacklist();
        blacklist.add("expired", System.currentTimeMillis() - 1);
        assertFalse(blacklist.contains("expired"));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行（在 `backendCode/ai-project`）：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=TokenBlacklistTest`
预期：FAIL（编译错误 `找不到符号 TokenBlacklist`）

- [ ] **步骤 3：编写最少实现代码**

```java
package com.ai.aiproject.config;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 Token 黑名单：logout 后将 JWT 拉黑至其过期时刻。
 * 单实例有效、重启失效（无持久化）；条目在 contains() 时惰性清理。
 * 后续如需集群共享/持久化，可替换为 Redis 实现（SETEX + EXISTS），调用方不变。
 */
@Component
public class TokenBlacklist {

    /** token -> 过期时刻（毫秒时间戳，取自 JWT exp） */
    private final ConcurrentHashMap<String, Long> blacklist = new ConcurrentHashMap<>();

    public void add(String token, long expireAtMs) {
        if (expireAtMs > System.currentTimeMillis()) {
            blacklist.put(token, expireAtMs);
        }
    }

    public boolean contains(String token) {
        Long expireAtMs = blacklist.get(token);
        if (expireAtMs == null) {
            return false;
        }
        if (expireAtMs <= System.currentTimeMillis()) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    public void remove(String token) {
        blacklist.remove(token);
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=TokenBlacklistTest`
预期：PASS（3 个用例通过，构建 `BUILD SUCCESS`）

- [ ] **步骤 5：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/config/TokenBlacklist.java" "backendCode/ai-project/src/test/java/com/ai/aiproject/config/TokenBlacklistTest.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(auth): 新增内存 Token 黑名单组件及单元测试"
```

---

### 任务 2：JWT 过滤器接入黑名单并透传原始 token

**文件：**
- 修改：`backendCode/ai-project/src/main/java/com/ai/aiproject/config/JwtAuthenticationFilter.java`
- 修改：`backendCode/ai-project/src/main/java/com/ai/aiproject/config/SecurityConfig.java`

- [ ] **步骤 1：修改 JwtAuthenticationFilter**

① 字段与构造器（第 26-36 行区域）：

```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 认证通过后，原始 JWT 存入 request attribute 的 key（供 controller 复用） */
    public static final String RAW_TOKEN_ATTR = "jwtRawToken";

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtTool jwtTool;
    private final JwtConfig jwtConfig;
    private final TokenBlacklist tokenBlacklist;

    public JwtAuthenticationFilter(JwtTool jwtTool, JwtConfig jwtConfig, TokenBlacklist tokenBlacklist) {
        this.jwtTool = jwtTool;
        this.jwtConfig = jwtConfig;
        this.tokenBlacklist = tokenBlacklist;
    }
```

② 认证成功分支（原第 71-77 行 `try` 块）替换为：

```java
        try {
            DecodedJWT jwt = jwtTool.parseToken(token.trim());
            // 黑名单校验：已登出的 token 一律拒绝
            if (tokenBlacklist.contains(token.trim())) {
                ResponseWriteTool.writeError(response, ResultCode.TOKEN_BLOCKED.getCode(), ResultCode.TOKEN_BLOCKED.getMsg());
                return;
            }
            Long userId = jwt.getClaim("userId").asLong();
            request.setAttribute(RAW_TOKEN_ATTR, token.trim());   // 透传原始 token 供 logout 使用
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (TokenExpiredException e) {
            ResponseWriteTool.writeError(response, ResultCode.TOKEN_EXPIRED.getCode(), ResultCode.TOKEN_EXPIRED.getMsg());
        } catch (JWTVerificationException e) {
            ResponseWriteTool.writeError(response, ResultCode.TOKEN_INVALID.getCode(), ResultCode.TOKEN_INVALID.getMsg());
        }
```

- [ ] **步骤 2：修改 SecurityConfig 的过滤器 Bean 构造**

原（第 25-28 行）：

```java
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTool jwtTool, JwtConfig jwtConfig) {
        return new JwtAuthenticationFilter(jwtTool, jwtConfig);
    }
```

改为：

```java
    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtTool jwtTool, JwtConfig jwtConfig, TokenBlacklist tokenBlacklist) {
        return new JwtAuthenticationFilter(jwtTool, jwtConfig, tokenBlacklist);
    }
```

（同包 config，无需新增 import。）

- [ ] **步骤 3：编译验证**

运行（在 `backendCode/ai-project`）：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=TokenBlacklistTest`
预期：PASS（`BUILD SUCCESS`，说明改动已通过编译）

- [ ] **步骤 4：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/config/JwtAuthenticationFilter.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/config/SecurityConfig.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(auth): JWT 过滤器接入 Token 黑名单并透传原始 token"
```

---

### 任务 3：Service 与 Controller 实现 logout

**文件：**
- 修改：`backendCode/ai-project/src/main/java/com/ai/aiproject/service/UserService.java`
- 修改：`backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/UserServiceImpl.java`
- 修改：`backendCode/ai-project/src/main/java/com/ai/aiproject/controller/UserController.java`

- [ ] **步骤 1：UserService 接口新增方法**

在 `getCurrentUserInfo();` 之后追加：

```java
    /**
     * 用户登出：将当前 JWT 加入黑名单直至其过期
     * @param token 原始 JWT（过滤器已校验通过并透传）
     */
    void logout(String token);
```

- [ ] **步骤 2：UserServiceImpl 实现**

① 类上追加注入字段（`@RequiredArgsConstructor` 风格，紧邻现有 final 字段）：

```java
    private final TokenBlacklist tokenBlacklist;
```

② 新增 import：

```java
import com.ai.aiproject.config.TokenBlacklist;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
```

③ 文件末尾追加实现：

```java
    @Override
    public void logout(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        try {
            DecodedJWT jwt = jwtUtil.parseToken(token);
            tokenBlacklist.add(token, jwt.getExpiresAt().getTime());
        } catch (JWTVerificationException e) {
            // token 已过期或无效：无需拉黑（过滤器在有效 token 请求时才会放行到此处）
        }
    }
```

- [ ] **步骤 3：UserController 新增 logout 端点**

① 新增 import：

```java
import com.ai.aiproject.config.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
```

② 在 `current()` 方法后追加：

```java
    /**
     * 用户登出
     * <p>
     * POST /api/user/logout（需登录态；过滤器已把原始 token 写入 request attribute）
     *
     * @return 统一成功响应（幂等：无 token 也返回成功）
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = (String) request.getAttribute(JwtAuthenticationFilter.RAW_TOKEN_ATTR);
        if (token != null) {
            userService.logout(token);
        }
        return Result.ok();
    }
```

- [ ] **步骤 4：编译 + 单测验证**

运行（在 `backendCode/ai-project`）：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=TokenBlacklistTest`
预期：PASS（`BUILD SUCCESS`）

- [ ] **步骤 5：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/service/UserService.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/UserServiceImpl.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/controller/UserController.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(auth): 实现 POST /api/user/logout 拉黑当前 JWT"
```

---

### 任务 4：前端登出改为 finally 兜底

**文件：**
- 修改：`frontendCode/ai-vue/src/components/Navbar.vue`（第 45-51 行）
- 修改：`frontendCode/ai-vue/src/components/FrontendLayout.vue`（第 45-51 行）

- [ ] **步骤 1：Navbar.vue**

原：

```js
            logout().then(() => {
                // 清除缓存
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                // 跳转到登录页
                router.push('/auth/login')
            })
```

改为（无论接口成败都清理本地并跳转，覆盖 token 过期等失败场景）：

```js
            logout().finally(() => {
                // 无论接口成败都清理本地状态（token 过期/黑名单等失败场景也能退出）
                localStorage.removeItem('token')
                localStorage.removeItem('userInfo')
                // 跳转到登录页
                router.push('/auth/login')
            })
```

- [ ] **步骤 2：FrontendLayout.vue**

原（第 44-52 行 `handleLogout`）：

```js
const handleLogout = () => {
    logout().then(() => {
        // 清除缓存
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        // 跳转到登录页
        router.push('/auth/login')
    })
}
```

改为：

```js
const handleLogout = () => {
    logout().finally(() => {
        // 无论接口成败都清理本地状态（token 过期/黑名单等失败场景也能退出）
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        // 跳转到登录页
        router.push('/auth/login')
    })
}
```

- [ ] **步骤 3：前端构建验证**

运行（在 `frontendCode/ai-vue`，esbuild 子进程受限时需以 `danger-full-access` 权限重试同一条命令）：`npm run build`
预期：`✓ built in ...`（vite 产物生成）

- [ ] **步骤 4：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "frontendCode/ai-vue/src/components/Navbar.vue" "frontendCode/ai-vue/src/components/FrontendLayout.vue"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "fix(frontend): 登出改用 finally 兜底清理本地登录状态"
```

---

### 任务 5：文档更新与收尾验证

**文件：**
- 修改：`backendCode/ai-project/CLAUDE.md`
- 修改：`docs/接口缺口清单.md`（状态标注 + 日期修正 2026-02-14 → 2026-09-08）

- [ ] **步骤 1：CLAUDE.md**

①「已实现接口」表在“用户 | `GET /api/user/current`”行后追加一行：

```markdown
| 用户 | `POST /api/user/logout` | ✅ 登出：需登录态；将当前 JWT 加入内存黑名单（config/TokenBlacklist，至 token 过期失效，重启清空）；前端登出已改 finally 兜底 |
```

②「目录结构」树中 config/ 行尾追加：

```text
├── config/                        # JwtConfig、SecurityConfig、JwtAuthenticationFilter、DbChatMemory（基于 DB 的 ChatMemory，会话记忆）、TokenBlacklist（内存 JWT 黑名单）
```

- [ ] **步骤 2：docs/接口缺口清单.md**

① 头部“生成日期”与 §3 中三处 `2026-02-14` 全部改为 `2026-09-08`（替换 `2026-02-14` → `2026-09-08`）。

② 缺口表 §2.6 行（`POST /user/logout`）标注已实现：

```markdown
| 16 | `POST /user/logout` | 无 body | 无消费（前端自行清 localStorage） | Navbar.vue:45、FrontendLayout.vue | ✅ 2026-09-08 已实现（内存黑名单方案 B） |
```

③ 表头行同步加一列“状态”（或在该行内标注，保持表格列数一致即可）。

- [ ] **步骤 3：整体验证**

运行（在 `backendCode/ai-project`）：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=TokenBlacklistTest`
预期：PASS（`BUILD SUCCESS`，编译覆盖全部主代码与测试代码）

（可选，需本地 MySQL 与 DASHSCOPE_KEY 环境）运行 `mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" spring-boot:run` 后按规格 §7 手动链路验收。

- [ ] **步骤 4：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/CLAUDE.md" "docs/接口缺口清单.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 标注 logout 已实现并修正文档日期"
```
