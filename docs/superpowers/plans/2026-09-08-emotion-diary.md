# 实现计划：情绪日记模块（emotion-diary 3 接口）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法跟踪进度。
> 仓库根（git 与相对路径基准）：`E:\Ai-Code\DeepSeek\心理健康Ai助手`
> Maven 必须带设置文件：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" ...`
> 本环境注意：Mockito/ByteBuddy self-attach 不可用 → 只用**纯 JVM 单测**（不依赖 Spring/Mockito）；esbuild 构建如遇 EPERM 需以 `danger-full-access` 重试同一条命令。

**目标：** 实现情绪日记 3 接口：`POST /api/emotion-diary`（用户提交 + best-effort AI 分析）、`GET /api/emotion-diary/admin/page`（管理员分页+筛选+用户名补全）、`DELETE /api/emotion-diary/admin/{id}`（管理员删除），与前端 `emotionDiary.vue`/`emotional.vue` 契约完全一致、零前端改动。

**架构：** 新增 `emotion_diary` 表与实体/Mapper；新增 `MybatisPlusConfig` 分页拦截器；Service 层实现三类业务（含 `requireAdmin()` 权限校验、`EmotionDiaryTool` 纯静态校验/范围解析、AI 分析走 `chatModel.call` best-effort 并规范化 JSON）；Controller 挂 `@RequestMapping("/api/emotion-diary")`。

**技术栈：** Spring Boot 3.4.1 / Java 17、MyBatis-Plus 3.5.16、Spring Security（权限复用）、spring-ai（qwen）、Hutool JSON、JUnit5（纯 JVM）。

---

### 任务 0：设计规格与计划入库

- [ ] **步骤 1：提交（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "docs/superpowers/specs/2026-09-08-emotion-diary-design.md" "docs/superpowers/plans/2026-09-08-emotion-diary.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 情绪日记模块设计规格与实现计划"
```

---

### 任务 1：DDL + 实体 + Mapper + 分页拦截器

**文件：**
- 创建：`docs/sql/2026-09-08-emotion-diary.sql`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/entity/EmotionDiary.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/EmotionDiaryMapper.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/config/MybatisPlusConfig.java`

- [ ] **步骤 1：DDL 文件**

```sql
-- 情绪日记表（人工执行：mysql -uroot -p123456 mental_health_assistant < 本文件）
CREATE TABLE IF NOT EXISTS emotion_diary (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    diary_date DATE NOT NULL COMMENT '记录日期',
    mood_score TINYINT NOT NULL COMMENT '情绪评分 1-10',
    dominant_emotion VARCHAR(20) DEFAULT NULL COMMENT '主要情绪（中文）',
    emotion_triggers VARCHAR(1000) DEFAULT NULL COMMENT '情绪触发因素',
    diary_content VARCHAR(2000) DEFAULT NULL COMMENT '日记内容',
    sleep_quality TINYINT DEFAULT NULL COMMENT '睡眠质量 1-5',
    stress_level TINYINT DEFAULT NULL COMMENT '压力水平 1-5',
    ai_emotion_analysis TEXT COMMENT 'AI 情绪分析 JSON 字符串',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_user_date (user_id, diary_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='情绪日记表';
```

- [ ] **步骤 2：实体 EmotionDiary**

```java
package com.ai.aiproject.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("emotion_diary")
public class EmotionDiary {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("diary_date")
    private LocalDate diaryDate;

    @TableField("mood_score")
    private Integer moodScore;

    @TableField("dominant_emotion")
    private String dominantEmotion;

    @TableField("emotion_triggers")
    private String emotionTriggers;

    @TableField("diary_content")
    private String diaryContent;

    @TableField("sleep_quality")
    private Integer sleepQuality;

    @TableField("stress_level")
    private Integer stressLevel;

    @TableField("ai_emotion_analysis")
    private String aiEmotionAnalysis;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 3：Mapper**

```java
package com.ai.aiproject.mapper;

import com.ai.aiproject.entity.EmotionDiary;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 情绪日记 Mapper（@MapperScan 已覆盖 com.ai.aiproject.mapper）
 */
public interface EmotionDiaryMapper extends BaseMapper<EmotionDiary> {
}
```

- [ ] **步骤 4：分页拦截器（selectPage 依赖，全项目生效）**

```java
package com.ai.aiproject.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 插件配置：分页拦截器
 */
@Configuration
public class MybatisPlusConfig {

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

- [ ] **步骤 5：编译验证**

运行（在 `backendCode/ai-project`）：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=TokenBlacklistTest`
预期：PASS（`BUILD SUCCESS`，新增类通过编译）

- [ ] **步骤 6：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "docs/sql/2026-09-08-emotion-diary.sql" "backendCode/ai-project/src/main/java/com/ai/aiproject/entity/EmotionDiary.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/EmotionDiaryMapper.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/config/MybatisPlusConfig.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(diary): 情绪日记建表 DDL、实体/Mapper 与 MyBatis-Plus 分页拦截器"
```

---

### 任务 2：DTO + 纯静态工具（TDD：先写失败测试）

**文件：**
- 测试：`backendCode/ai-project/src/test/java/com/ai/aiproject/Utils/EmotionDiaryToolTest.java`（新增）
- 实现：`backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/EmotionDiaryTool.java`（新增）
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/dto/command/EmotionDiaryAddCommandDTO.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/dto/query/EmotionDiaryAdminPageQueryDTO.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/EmotionDiaryAdminItemDTO.java`

- [ ] **步骤 1：编写失败的测试**

```java
package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmotionDiaryToolTest {

    @Test
    void parseValidRange() {
        assertArrayEquals(new int[]{1, 3}, EmotionDiaryTool.parseMoodScoreRange("1-3"));
        assertArrayEquals(new int[]{4, 6}, EmotionDiaryTool.parseMoodScoreRange("4-6"));
        assertArrayEquals(new int[]{7, 10}, EmotionDiaryTool.parseMoodScoreRange("7-10"));
    }

    @Test
    void parseBlankRangeReturnsNull() {
        assertNull(EmotionDiaryTool.parseMoodScoreRange(null));
        assertNull(EmotionDiaryTool.parseMoodScoreRange("  "));
    }

    @Test
    void parseInvalidRangeThrows() {
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("abc"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("1-11"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("0-3"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("5-1"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.parseMoodScoreRange("1-3-5"));
    }

    @Test
    void dominantEmotionValidation() {
        assertDoesNotThrow(() -> EmotionDiaryTool.validateDominantEmotion(null));
        assertDoesNotThrow(() -> EmotionDiaryTool.validateDominantEmotion(""));
        assertDoesNotThrow(() -> EmotionDiaryTool.validateDominantEmotion("开心"));
        assertThrows(BusinessException.class, () -> EmotionDiaryTool.validateDominantEmotion("外星情绪"));
    }
}
```

- [ ] **步骤 2：运行测试验证失败**

运行（在 `backendCode/ai-project`）：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=EmotionDiaryToolTest`
预期：FAIL（编译错误 `找不到符号 EmotionDiaryTool`）

- [ ] **步骤 3：编写最少实现 EmotionDiaryTool**

```java
package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.enums.ResultCode;

import java.util.Set;

/**
 * 情绪日记模块纯静态工具（便于无 Spring/Mockito 的单元测试）
 */
public final class EmotionDiaryTool {

    /** 前端可选的主要情绪（与 emotionDiary.vue emotionOptions 一致） */
    private static final Set<String> DOMINANT_EMOTIONS =
            Set.of("开心", "平静", "焦虑", "悲伤", "兴奋", "疲惫", "惊讶", "困惑");

    private EmotionDiaryTool() {
    }

    /**
     * 校验主要情绪：空值放行，非空必须命中前端枚举
     */
    public static void validateDominantEmotion(String dominantEmotion) {
        if (dominantEmotion != null && !dominantEmotion.isBlank() && !DOMINANT_EMOTIONS.contains(dominantEmotion)) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的主要情绪");
        }
    }

    /**
     * 解析前端评分范围筛选（'1-3'/'4-6'/'7-10'）
     *
     * @return [low, high]；空值返回 null；非法值抛 PARAM_INVALID
     */
    public static int[] parseMoodScoreRange(String range) {
        if (range == null || range.isBlank()) {
            return null;
        }
        String[] parts = range.split("-");
        if (parts.length != 2) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的情绪评分范围");
        }
        int low;
        int high;
        try {
            low = Integer.parseInt(parts[0].trim());
            high = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的情绪评分范围");
        }
        if (low < 1 || high > 10 || low > high) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的情绪评分范围");
        }
        return new int[]{low, high};
    }
}
```

- [ ] **步骤 4：运行测试验证通过**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=EmotionDiaryToolTest`
预期：PASS（4 个用例通过，`BUILD SUCCESS`）

- [ ] **步骤 5：创建三个 DTO**

`dto/command/EmotionDiaryAddCommandDTO.java`：

```java
package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新增情绪日记命令 DTO
 */
@Data
public class EmotionDiaryAddCommandDTO {

    @NotNull(message = "记录日期不能为空")
    private LocalDate diaryDate;

    @NotNull(message = "情绪评分不能为空")
    @Min(value = 1, message = "情绪评分必须在1-10之间")
    @Max(value = 10, message = "情绪评分必须在1-10之间")
    private Integer moodScore;

    @Size(max = 20, message = "主要情绪长度不能超过20个字符")
    private String dominantEmotion;

    @Size(max = 1000, message = "情绪触发因素长度不能超过1000个字符")
    private String emotionTriggers;

    @Size(max = 2000, message = "日记内容长度不能超过2000个字符")
    private String diaryContent;

    @Min(value = 1, message = "睡眠质量必须在1-5之间")
    @Max(value = 5, message = "睡眠质量必须在1-5之间")
    private Integer sleepQuality;

    @Min(value = 1, message = "压力水平必须在1-5之间")
    @Max(value = 5, message = "压力水平必须在1-5之间")
    private Integer stressLevel;
}
```

`dto/query/EmotionDiaryAdminPageQueryDTO.java`（GET 参数自动绑定 POJO，保留前端拼写 `moodScreRange`）：

```java
package com.ai.aiproject.dto.query;

import lombok.Data;

/**
 * 管理端情绪日记分页查询 DTO
 */
@Data
public class EmotionDiaryAdminPageQueryDTO {

    private long current = 1;

    private long size = 10;

    /** 用户ID（字符串，宽松解析） */
    private String userId;

    /** 评分范围筛选，前端拼写 moodScreRange：'1-3'|'4-6'|'7-10' */
    private String moodScreRange;
}
```

`dto/response/EmotionDiaryAdminItemDTO.java`：

```java
package com.ai.aiproject.dto.response;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 管理端情绪日记列表/详情行 DTO
 */
@Data
public class EmotionDiaryAdminItemDTO {

    private Long id;

    private Long userId;

    private String username;

    private String nickname;

    private LocalDate diaryDate;

    private Integer moodScore;

    private String dominantEmotion;

    private Integer sleepQuality;

    private Integer stressLevel;

    private String emotionTriggers;

    private String diaryContent;

    /** AI 情绪分析 JSON 字符串（前端 JSON.parse） */
    private String aiEmotionAnalysis;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 6：编译验证 + Commit（如仓库启用版本管理）**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=EmotionDiaryToolTest`
预期：PASS（`BUILD SUCCESS`）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/test/java/com/ai/aiproject/Utils/EmotionDiaryToolTest.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/EmotionDiaryTool.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/command/EmotionDiaryAddCommandDTO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/query/EmotionDiaryAdminPageQueryDTO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/EmotionDiaryAdminItemDTO.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(diary): 情绪日记 DTO 与纯静态校验/范围解析工具（含单测）"
```

---

### 任务 3：Service（含 AI 分析）+ 提示词常量

**文件：**
- 修改：`backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/PromptManage.java`（追加常量）
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/service/EmotionDiaryService.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/EmotionDiaryServiceImpl.java`

- [ ] **步骤 1：PromptManage 追加常量**

在 `PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT` 常量后、`private PromptManage() {` 前插入：

```java
    /**
     * 情绪日记 AI 情绪分析系统提示词（要求只输出规范化 JSON）
     */
    public static final String DIARY_EMOTION_ANALYSIS_SYSTEM_PROMPT =
            "你是心理健康助手中的情绪分析专家。请根据用户提交的情绪日志（情绪评分1-10、主要情绪、触发因素、日记内容、睡眠质量1-5、压力水平1-5）进行分析。\n" +
            "请严格只输出一个 JSON 对象（不要输出任何解释、前后缀或 markdown 代码块），字段与要求如下：\n" +
            "{\n" +
            "  \"primaryEmotion\": \"主要情绪中文词\",\n" +
            "  \"emotionScore\": 0到100的整数,\n" +
            "  \"isNegative\": true或false,\n" +
            "  \"riskLevel\": 0到3的整数(0正常/1关注/2预警/3危机),\n" +
            "  \"suggestion\": \"给用户的专业建议\",\n" +
            "  \"riskDescription\": \"风险描述\",\n" +
            "  \"improvementSuggestions\": [\"可执行的改善建议1\", \"改善建议2\"]\n" +
            "}\n" +
            "要求：字段必须齐全；improvementSuggestions 为字符串数组（可为空数组）。";
```

- [ ] **步骤 2：Service 接口**

```java
package com.ai.aiproject.service;

import com.ai.aiproject.dto.command.EmotionDiaryAddCommandDTO;
import com.ai.aiproject.dto.query.EmotionDiaryAdminPageQueryDTO;
import com.ai.aiproject.dto.response.EmotionDiaryAdminItemDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 情绪日记服务
 */
public interface EmotionDiaryService {

    /**
     * 用户新增日记（含 best-effort AI 情绪分析落库）
     */
    void addDiary(EmotionDiaryAddCommandDTO commandDTO);

    /**
     * 管理端分页查询（需管理员；含用户昵称/用户名补全）
     */
    Page<EmotionDiaryAdminItemDTO> adminPage(EmotionDiaryAdminPageQueryDTO queryDTO);

    /**
     * 管理端删除（幂等，需管理员）
     */
    void adminDelete(Long id);
}
```

- [ ] **步骤 3：Service 实现（完整文件）**

```java
package com.ai.aiproject.service.Impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.EmotionDiaryTool;
import com.ai.aiproject.Utils.PromptManage;
import com.ai.aiproject.Utils.SecurityContextTool;
import com.ai.aiproject.dto.command.EmotionDiaryAddCommandDTO;
import com.ai.aiproject.dto.query.EmotionDiaryAdminPageQueryDTO;
import com.ai.aiproject.dto.response.EmotionDiaryAdminItemDTO;
import com.ai.aiproject.entity.EmotionDiary;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.enums.UserType;
import com.ai.aiproject.mapper.EmotionDiaryMapper;
import com.ai.aiproject.mapper.UserMapper;
import com.ai.aiproject.service.EmotionDiaryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 情绪日记服务实现
 */
@Service
@RequiredArgsConstructor
public class EmotionDiaryServiceImpl implements EmotionDiaryService {

    private final EmotionDiaryMapper emotionDiaryMapper;
    private final UserMapper userMapper;
    private final OpenAiChatModel chatModel;

    @Override
    public void addDiary(EmotionDiaryAddCommandDTO commandDTO) {
        // 1. 当前用户
        Long userId = SecurityContextTool.getCurrentUserId();
        // 2. 主要情绪白名单校验（其余由 @Valid 校验）
        EmotionDiaryTool.validateDominantEmotion(commandDTO.getDominantEmotion());
        // 3. 组装实体
        LocalDateTime now = LocalDateTime.now();
        EmotionDiary diary = EmotionDiary.builder()
                .userId(userId)
                .diaryDate(commandDTO.getDiaryDate())
                .moodScore(commandDTO.getMoodScore())
                .dominantEmotion(commandDTO.getDominantEmotion())
                .emotionTriggers(commandDTO.getEmotionTriggers())
                .diaryContent(commandDTO.getDiaryContent())
                .sleepQuality(commandDTO.getSleepQuality())
                .stressLevel(commandDTO.getStressLevel())
                .aiEmotionAnalysis(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // 4. best-effort AI 情绪分析（有正文才调用；失败静默降级，不阻塞保存）
        if (hasText(commandDTO.getDiaryContent()) || hasText(commandDTO.getEmotionTriggers())) {
            try {
                diary.setAiEmotionAnalysis(analyzeEmotion(diary));
            } catch (Exception e) {
                diary.setAiEmotionAnalysis(null);
            }
        }
        emotionDiaryMapper.insert(diary);
    }

    @Override
    public Page<EmotionDiaryAdminItemDTO> adminPage(EmotionDiaryAdminPageQueryDTO queryDTO) {
        requireAdmin();
        long current = queryDTO.getCurrent() > 0 ? queryDTO.getCurrent() : 1;
        long size = queryDTO.getSize() > 0 ? queryDTO.getSize() : 10;
        int[] range = EmotionDiaryTool.parseMoodScoreRange(queryDTO.getMoodScreRange());

        LambdaQueryWrapper<EmotionDiary> wrapper = Wrappers.<EmotionDiary>lambdaQuery()
                .eq(hasText(queryDTO.getUserId()), EmotionDiary::getUserId, toUserId(queryDTO.getUserId()))
                .between(range != null, EmotionDiary::getMoodScore,
                        range == null ? 0 : range[0], range == null ? 0 : range[1])
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getId);

        Page<EmotionDiary> diaryPage = emotionDiaryMapper.selectPage(new Page<>(current, size), wrapper);

        // 用户信息批量补全（username/nickname）
        List<Long> userIds = diaryPage.getRecords().stream()
                .map(EmotionDiary::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        Page<EmotionDiaryAdminItemDTO> result = new Page<>(diaryPage.getCurrent(), diaryPage.getSize(), diaryPage.getTotal());
        result.setRecords(diaryPage.getRecords().stream().map(d -> {
            EmotionDiaryAdminItemDTO vo = new EmotionDiaryAdminItemDTO();
            vo.setId(d.getId());
            vo.setUserId(d.getUserId());
            User u = userMap.get(d.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setNickname(u.getNickname());
            }
            vo.setDiaryDate(d.getDiaryDate());
            vo.setMoodScore(d.getMoodScore());
            vo.setDominantEmotion(d.getDominantEmotion());
            vo.setSleepQuality(d.getSleepQuality());
            vo.setStressLevel(d.getStressLevel());
            vo.setEmotionTriggers(d.getEmotionTriggers());
            vo.setDiaryContent(d.getDiaryContent());
            vo.setAiEmotionAnalysis(d.getAiEmotionAnalysis());
            vo.setCreatedAt(d.getCreatedAt());
            vo.setUpdatedAt(d.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void adminDelete(Long id) {
        requireAdmin();
        if (id != null) {
            emotionDiaryMapper.deleteById(id);
        }
    }

    /**
     * 调用 qwen 对日记做情绪分析，返回规范化 JSON 字符串
     */
    private String analyzeEmotion(EmotionDiary diary) {
        String userInput = "情绪评分(1-10)：" + diary.getMoodScore()
                + "\n主要情绪：" + (diary.getDominantEmotion() == null ? "未填写" : diary.getDominantEmotion())
                + "\n睡眠质量(1-5)：" + (diary.getSleepQuality() == null ? "未填写" : diary.getSleepQuality())
                + "\n压力水平(1-5)：" + (diary.getStressLevel() == null ? "未填写" : diary.getStressLevel())
                + "\n情绪触发因素：" + (diary.getEmotionTriggers() == null ? "未填写" : diary.getEmotionTriggers())
                + "\n日记内容：" + (diary.getDiaryContent() == null ? "未填写" : diary.getDiaryContent());
        Prompt prompt = new Prompt(java.util.List.of(
                new SystemMessage(PromptManage.DIARY_EMOTION_ANALYSIS_SYSTEM_PROMPT),
                new UserMessage(userInput)));
        var response = chatModel.call(prompt);
        String raw = response.getResult().getOutput().getContent();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 容错：剥离可能的 ```json ... ``` 包裹
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
        }
        JSONObject obj = JSONUtil.parseObj(json);
        // 规范化：字段齐全 + 数值夹取 + 数组兜底，保证与前端 JSON.parse 消费一致
        int score = obj.getInt("emotionScore", 0);
        score = Math.max(0, Math.min(100, score));
        int risk = obj.getInt("riskLevel", 0);
        risk = Math.max(0, Math.min(3, risk));
        JSONArray improvements = obj.getJSONArray("improvementSuggestions");
        if (improvements == null) {
            improvements = new JSONArray();
        }
        return JSONUtil.createObj()
                .set("primaryEmotion", obj.getStr("primaryEmotion", ""))
                .set("emotionScore", score)
                .set("isNegative", Boolean.TRUE.equals(obj.getBool("isNegative")))
                .set("riskLevel", risk)
                .set("suggestion", obj.getStr("suggestion", ""))
                .set("riskDescription", obj.getStr("riskDescription", ""))
                .set("improvementSuggestions", improvements)
                .toString();
    }

    /**
     * 校验当前登录用户为管理员，否则抛 A0301
     */
    private void requireAdmin() {
        Long userId = SecurityContextTool.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !UserType.ADMIN.getCode().equals(user.getUserType())) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(), ResultCode.ACCESS_UNAUTHORIZED.getMsg());
        }
    }

    private Long toUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的用户ID");
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
```

- [ ] **步骤 4：编译验证 + Commit（如仓库启用版本管理）**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=EmotionDiaryToolTest`
预期：PASS（`BUILD SUCCESS`）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/PromptManage.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/EmotionDiaryService.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/EmotionDiaryServiceImpl.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(diary): 情绪日记 Service（新增/AI分析/管理端分页与删除/管理员校验）"
```

---

### 任务 4：Controller

**文件：**
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/controller/EmotionDiaryController.java`

- [ ] **步骤 1：Controller（完整文件）**

```java
package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.command.EmotionDiaryAddCommandDTO;
import com.ai.aiproject.dto.query.EmotionDiaryAdminPageQueryDTO;
import com.ai.aiproject.dto.response.EmotionDiaryAdminItemDTO;
import com.ai.aiproject.service.EmotionDiaryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪日记控制器
 * <p>
 * 前缀 /api/emotion-diary
 */
@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    @Resource
    private EmotionDiaryService emotionDiaryService;

    /**
     * 用户新增日记
     * POST /api/emotion-diary
     */
    @PostMapping
    public Result<Void> add(@Valid @RequestBody EmotionDiaryAddCommandDTO commandDTO) {
        emotionDiaryService.addDiary(commandDTO);
        return Result.ok();
    }

    /**
     * 管理端分页查询（需管理员）
     * GET /api/emotion-diary/admin/page
     */
    @GetMapping("/admin/page")
    public Result<Page<EmotionDiaryAdminItemDTO>> adminPage(EmotionDiaryAdminPageQueryDTO queryDTO) {
        return Result.ok(emotionDiaryService.adminPage(queryDTO));
    }

    /**
     * 管理端删除（需管理员，幂等）
     * DELETE /api/emotion-diary/admin/{id}
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> adminDelete(@PathVariable Long id) {
        emotionDiaryService.adminDelete(id);
        return Result.ok();
    }
}
```

- [ ] **步骤 2：编译验证 + Commit（如仓库启用版本管理）**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=EmotionDiaryToolTest,TokenBlacklistTest`
预期：PASS（`BUILD SUCCESS`）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/controller/EmotionDiaryController.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(diary): 情绪日记 Controller（POST 新增/GET admin page/DELETE admin id）"
```

---

### 任务 5：文档更新与收尾验证

**文件：**
- 修改：`backendCode/ai-project/CLAUDE.md`
- 修改：`docs/接口缺口清单.md`

- [ ] **步骤 1：CLAUDE.md 更新**

①「已实现接口」表末行（“其余 | 情绪日记/知识库/文件/分析 | ❌ 未实现”）前插入三行：

```markdown
| 日记 | `POST /api/emotion-diary` | ✅ 用户记日记：user_id 取当前登录用户；主要情绪白名单校验；有正文时 best-effort 调 qwen 情绪分析（失败留空不阻塞保存）；写入 emotion_diary |
| 日记 | `GET /api/emotion-diary/admin/page` | ✅ 管理员分页（current/size + userId + moodScreRange('1-3'/'4-6'/'7-10')）；返回 MyBatis-Plus Page{records,total}，行含 username/nickname（user 表补全）与 aiEmotionAnalysis(JSON 串)；非管理员 A0301 |
| 日记 | `DELETE /api/emotion-diary/admin/{id}` | ✅ 管理员删除（幂等） |
```

同时把“其余”行改为：

```markdown
| 其余 | 知识库/文件/分析 | ❌ 未实现 |
```

②「目录结构（当前）」追加：

```text
├── config/MybatisPlusConfig.java  # MyBatis-Plus 分页拦截器（PaginationInnerInterceptor）
├── entity/EmotionDiary.java、mapper/EmotionDiaryMapper.java
├── controller/EmotionDiaryController.java、service/EmotionDiaryService(.Impl)（新增/AI 分析/管理端分页删除）
├── Utils/EmotionDiaryTool.java     # 主要情绪白名单 + moodScreRange 解析（纯静态，含单测）
├── dto/command/EmotionDiaryAddCommandDTO、dto/query/EmotionDiaryAdminPageQueryDTO、dto/response/EmotionDiaryAdminItemDTO
```

- [ ] **步骤 2：docs/接口缺口清单.md 更新**

缺口表 §2.2 三行标注状态（表头同步加“状态”列，见 logout 模块做法）：

```markdown
| 4 | `POST /emotion-diary` | ...（保持原行参数不变） | ✅ 2026-09-08 已实现（多条/日，有正文才 AI 分析） |
| 5 | `GET /emotion-diary/admin/page` | ... | ✅ 2026-09-08 已实现 |
| 6 | `DELETE /emotion-diary/admin/{id}` | ... | ✅ 2026-09-08 已实现 |
```

- [ ] **步骤 3：整体编译/单测验证**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=EmotionDiaryToolTest,TokenBlacklistTest`
预期：PASS（`BUILD SUCCESS`，7 个用例：EmotionDiaryToolTest 4 + TokenBlacklistTest 3）

（可选，需本地 MySQL 且已执行 DDL、DASHSCOPE_KEY 可用）运行 `mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" spring-boot:run` 后手动验收：
1. 注册普通用户 → POST /api/emotion-diary（含正文）→ 200；查库 ai_emotion_analysis 为 JSON；空正文日记 → 该字段 NULL。
2. 普通用户调 GET /api/emotion-diary/admin/page → code A0301。
3. 注册管理员（/api/user/add userType=2）→ 登录 → GET admin/page（含 moodScreRange=4-6 与 userId 筛选）→ {records,total}，行含 username/nickname；DELETE /admin/{id} → 200。
4. 非法主要情绪/非法 moodScreRange → PARAM_INVALID。

- [ ] **步骤 4：Commit（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/CLAUDE.md" "docs/接口缺口清单.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 标注情绪日记三接口已实现"
```
