# 实现计划：知识库模块（含 OSS 封面上传）

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）跟踪进度。
> 仓库根：`E:\Ai-Code\DeepSeek\心理健康Ai助手`；后端命令在 `backendCode/ai-project` 执行，Maven 一律 `mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" ...`。
> 环境注意：Mockito self-attach 不可用 → 只写纯 JVM 单测；esbuild EPERM 时以 `danger-full-access` 重试同一条命令；OSS 仅上传运行时需要 Secret（环境变量），编译/单测不依赖。

**目标：** 8 个接口（分类树、文章分页(管理员/用户双模式)、详情(+阅读数)、增/改/状态/删、OSS 封面上传）+ 2 张新表 + 前端图片前缀 2 处小改，使知识库前后端全链路可用。

**架构：** 实体/Mapper 对应新表；`KnowledgeArticleService` 单入口按当前用户角色分流（管理员全量 + 草稿可读写，普通用户只见已发布）；上传走 `FileStorageService` → OSS（公共读，返回相对 filePath，前端拼 url-prefix）；复用分页拦截器、JWT、Result 信封，错误码全部复用不新增。

**技术栈：** Spring Boot 3.4.1 / MyBatis-Plus 3.5.16 / 阿里云 OSS SDK 3.17.4 / Hutool / Vue3（仅配置行）。

---

### 任务 0：规格与计划入库

- [ ] **步骤 1：提交（如仓库启用版本管理）**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "docs/superpowers/specs/2026-09-08-knowledge-design.md" "docs/superpowers/plans/2026-09-08-knowledge.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 知识库模块设计规格与实现计划"
```

---

### 任务 1：建表 SQL + 实体 + Mapper

**文件：**
- 创建：`docs/sql/2026-09-08-knowledge.sql`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/entity/KnowledgeCategory.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/entity/KnowledgeArticle.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/KnowledgeCategoryMapper.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/KnowledgeArticleMapper.java`

- [ ] **步骤 1：DDL 文件**

```sql
-- 知识库分类表 + 文章表（授权自动执行：MYSQL_PWD=123456 mysql -uroot mental_health_assistant < 本文件）
CREATE TABLE IF NOT EXISTS knowledge_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_name VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0=顶级（预留层级）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文章分类表';

CREATE TABLE IF NOT EXISTS knowledge_article (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    summary VARCHAR(1000) DEFAULT NULL COMMENT '摘要',
    content LONGTEXT COMMENT '正文（富文本 HTML）',
    cover_image VARCHAR(255) DEFAULT NULL COMMENT '封面相对路径（OSS）',
    tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    author_id BIGINT NOT NULL COMMENT '作者(管理员)用户ID',
    read_count INT NOT NULL DEFAULT 0 COMMENT '阅读数',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
    published_at DATETIME DEFAULT NULL COMMENT '发布时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_status_published (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文章表';

-- 种子分类（幂等）
INSERT IGNORE INTO knowledge_category (category_name, parent_id, sort_order, created_at, updated_at) VALUES
('心理健康', 0, 1, NOW(), NOW()),
('情绪管理', 0, 2, NOW(), NOW()),
('压力应对', 0, 3, NOW(), NOW()),
('人际交往', 0, 4, NOW(), NOW()),
('自我成长', 0, 5, NOW(), NOW());
```

- [ ] **步骤 2：实体 KnowledgeCategory**

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

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_category")
public class KnowledgeCategory {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category_name")
    private String categoryName;

    @TableField("parent_id")
    private Long parentId;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 3：实体 KnowledgeArticle**

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

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("knowledge_article")
public class KnowledgeArticle {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("category_id")
    private Long categoryId;

    private String title;

    private String summary;

    private String content;

    @TableField("cover_image")
    private String coverImage;

    private String tags;

    @TableField("author_id")
    private Long authorId;

    @TableField("read_count")
    private Integer readCount;

    private Integer status;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 4：两个 Mapper**

```java
package com.ai.aiproject.mapper;

import com.ai.aiproject.entity.KnowledgeCategory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface KnowledgeCategoryMapper extends BaseMapper<KnowledgeCategory> {
}
```

```java
package com.ai.aiproject.mapper;

import com.ai.aiproject.entity.KnowledgeArticle;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface KnowledgeArticleMapper extends BaseMapper<KnowledgeArticle> {
}
```

- [ ] **步骤 5：尝试执行 DDL（本机 MySQL，授权过）**

运行（在仓库根）：`$env:MYSQL_PWD='123456'; if (Get-Command mysql -ErrorAction SilentlyContinue) { mysql -uroot mental_health_assistant < "E:\Ai-Code\DeepSeek\心理健康Ai助手\docs\sql\2026-09-08-knowledge.sql" 2>&1; if ($LASTEXITCODE -eq 0) { 'DDL OK' } else { 'DDL FAILED（转手动执行）' } } else { '无 mysql 客户端（转手动执行）' }`
预期：`DDL OK`，或提示转手动（记录到最终报告即可，不阻塞）。

- [ ] **步骤 6：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=TokenBlacklistTest,EmotionDiaryToolTest"`
预期：7/7 PASS（BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "docs/sql/2026-09-08-knowledge.sql" "backendCode/ai-project/src/main/java/com/ai/aiproject/entity/KnowledgeCategory.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/entity/KnowledgeArticle.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/KnowledgeCategoryMapper.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/mapper/KnowledgeArticleMapper.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(knowledge): 分类/文章建表 DDL、实体与 Mapper"
```

---

### 任务 2：OSS 基座（依赖/配置/属性/纯静态文件工具 TDD）

**文件：**
- 修改：`backendCode/ai-project/pom.xml`、`backendCode/ai-project/src/main/resources/application.yaml`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/config/OssProperties.java`
- 创建：`backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/OssFileTool.java`
- 测试：`backendCode/ai-project/src/test/java/com/ai/aiproject/Utils/OssFileToolTest.java`

- [ ] **步骤 1：pom.xml 追加依赖**

在 `mybatis-plus-jsqlparser` 依赖之后追加：

```xml
        <!-- 阿里云 OSS（封面上传存储） -->
        <dependency>
            <groupId>com.aliyun.oss</groupId>
            <artifactId>aliyun-sdk-oss</artifactId>
            <version>3.17.4</version>
        </dependency>
```

- [ ] **步骤 2：application.yaml 追加 OSS 配置**

在文件末尾（`token-prefix` 行后）追加：

```yaml
# 阿里云 OSS（AccessKey 从环境变量读取，禁止写死入库）
oss:
  endpoint: oss-cn-beijing.aliyuncs.com
  bucket: psychology-ai
  access-key-id: ${OSS_ACCESS_KEY_ID:}
  access-key-secret: ${OSS_ACCESS_KEY_SECRET:}
  url-prefix: https://psychology-ai.oss-cn-beijing.aliyuncs.com
```

- [ ] **步骤 3：OssProperties**

```java
package com.ai.aiproject.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 阿里云 OSS 配置（oss.*；AccessKey 取自环境变量）
 */
@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {

    private String endpoint;
    private String bucket;
    private String accessKeyId;
    private String accessKeySecret;
    private String urlPrefix;
}
```

- [ ] **步骤 4：编写失败的测试 OssFileToolTest**

```java
package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssFileToolTest {

    @Test
    void extractExtension() {
        assertEquals("png", OssFileTool.extractExt("a.PNG"));
        assertEquals("webp", OssFileTool.extractExt("x.webp"));
        assertEquals("", OssFileTool.extractExt("noext"));
        assertEquals("jpg", OssFileTool.extractExt("a.b.jpg"));
    }

    @Test
    void allowedImageCheck() {
        assertTrue(OssFileTool.isAllowedImage("c.png"));
        assertTrue(OssFileTool.isAllowedImage("c.jpeg"));
        assertTrue(OssFileTool.isAllowedImage("c.webp"));
        assertFalse(OssFileTool.isAllowedImage("c.txt"));
        assertFalse(OssFileTool.isAllowedImage("c.exe"));
    }

    @Test
    void validateImageFilePassAndFail() {
        assertDoesNotThrow(() -> OssFileTool.validateImageFile("ok.jpg", 1024));
        assertThrows(BusinessException.class, () -> OssFileTool.validateImageFile("bad.gif", 6L * 1024 * 1024)); // 超 5MB
        assertThrows(BusinessException.class, () -> OssFileTool.validateImageFile("bad.js", 1024));
    }

    @Test
    void buildObjectKeyMatchesPattern() {
        String key = OssFileTool.buildObjectKey("myPhoto.PNG");
        assertTrue(key.matches("article/cover/\\d{6}/[0-9a-f-]{36}\\.png"), key);
        assertFalse(key.startsWith("/"));
    }
}
```

- [ ] **步骤 5：运行测试验证失败**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=OssFileToolTest`
预期：FAIL（找不到符号 OssFileTool）

- [ ] **步骤 6：实现 OssFileTool**

```java
package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.enums.ResultCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * OSS 文件纯静态工具：扩展名校验/图片类型与大小校验/对象键生成（便于无 Spring/Mockito 单测）
 */
public final class OssFileTool {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 上传大小上限 5MB */
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private OssFileTool() {
    }

    /** 取小写扩展名（含点后部分），无扩展名返回 "" */
    public static String extractExt(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedImage(String filename) {
        return ALLOWED_EXT.contains(extractExt(filename));
    }

    /**
     * 校验：类型必须为图片且 ≤5MB
     * @throws BusinessException FILE_TYPE_NOT_SUPPORTED / FILE_SIZE_EXCEEDED
     */
    public static void validateImageFile(String originalFilename, long sizeBytes) {
        if (!isAllowedImage(originalFilename)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(), ResultCode.FILE_TYPE_NOT_SUPPORTED.getMsg());
        }
        if (sizeBytes > MAX_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), ResultCode.FILE_SIZE_EXCEEDED.getMsg());
        }
    }

    /**
     * 生成对象键（不带前导斜杠）：article/cover/yyyyMM/uuid.ext
     */
    public static String buildObjectKey(String originalFilename) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String ext = extractExt(originalFilename);
        return "article/cover/" + date + "/" + UUID.randomUUID() + "." + ext;
    }
}
```

- [ ] **步骤 7：运行测试验证通过**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=OssFileToolTest`
预期：PASS（4/4，BUILD SUCCESS）

- [ ] **步骤 8：Commit**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/pom.xml" "backendCode/ai-project/src/main/resources/application.yaml" "backendCode/ai-project/src/main/java/com/ai/aiproject/config/OssProperties.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/OssFileTool.java" "backendCode/ai-project/src/test/java/com/ai/aiproject/Utils/OssFileToolTest.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(knowledge): OSS 依赖/配置/属性与文件纯静态工具（含单测）"
```

---

### 任务 3：文件上传（Service + Controller + DTO）

**文件：**
- 创建：`dto/response/FileUploadResponseDTO.java`
- 创建：`service/FileStorageService.java`、`service/Impl/FileStorageServiceImpl.java`
- 创建：`controller/FileController.java`

- [ ] **步骤 1：DTO**

```java
package com.ai.aiproject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDTO {

    /** 相对路径（如 /article/cover/202609/uuid.jpg），前端拼 OSS url-prefix 显示 */
    private String filePath;
}
```

- [ ] **步骤 2：Service 接口与实现**

```java
package com.ai.aiproject.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务
 */
public interface FileStorageService {

    /**
     * 上传图片到 OSS
     * @param file 图片文件（≤5MB）
     * @return 相对路径（以 / 开头）
     */
    String uploadImage(MultipartFile file);
}
```

```java
package com.ai.aiproject.service.Impl;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.OssFileTool;
import com.ai.aiproject.config.OssProperties;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.service.FileStorageService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储服务实现（阿里云 OSS，公共读）
 */
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final OssProperties ossProperties;

    @Override
    public String uploadImage(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        OssFileTool.validateImageFile(originalName, file.getSize());
        String objectKey = OssFileTool.buildObjectKey(originalName);

        OSS ossClient = new OSSClientBuilder()
                .build(ossProperties.getEndpoint(), ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            ossClient.putObject(ossProperties.getBucket(), objectKey, in, meta);
            return "/" + objectKey;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), ResultCode.FILE_UPLOAD_FAILED.getMsg());
        } finally {
            ossClient.shutdown();
        }
    }
}
```

- [ ] **步骤 3：Controller**

```java
package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.response.FileUploadResponseDTO;
import com.ai.aiproject.service.FileStorageService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件控制器
 * <p>
 * POST /api/file/upload（需登录；业务字段 businessType/businessId/businessField 本期仅接收不落库）
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Resource
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<FileUploadResponseDTO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(new FileUploadResponseDTO(fileStorageService.uploadImage(file)));
    }
}
```

- [ ] **步骤 4：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=OssFileToolTest`
预期：PASS（BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/FileUploadResponseDTO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/FileStorageService.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/FileStorageServiceImpl.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/controller/FileController.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(knowledge): OSS 封面上传接口 POST /api/file/upload"
```

---

### 任务 4：权限工具 + 分类/文章 DTO 与 VO

**文件：**
- 创建：`Utils/AuthzTool.java`
- 创建：`dto/command/KnowledgeArticleSaveCommandDTO.java`、`dto/command/ArticleStatusChangeDTO.java`、`dto/query/KnowledgeArticlePageQueryDTO.java`
- 创建：`dto/response/KnowledgeCategoryVO.java`、`dto/response/KnowledgeArticlePageItemVO.java`、`dto/response/KnowledgeArticleDetailVO.java`

- [ ] **步骤 1：AuthzTool**

```java
package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.enums.UserType;
import com.ai.aiproject.mapper.UserMapper;

/**
 * 管理员鉴权工具（Service 层复用）
 */
public final class AuthzTool {

    private AuthzTool() {
    }

    /** 是否管理员；未登录/用户不存在一律返回 false */
    public static boolean isAdmin(UserMapper userMapper) {
        try {
            Long userId = SecurityContextTool.getCurrentUserId();
            User user = userMapper.selectById(userId);
            return user != null && UserType.ADMIN.getCode().equals(user.getUserType());
        } catch (BusinessException e) {
            return false;
        }
    }

    /** 非管理员抛 A0301 */
    public static void requireAdmin(UserMapper userMapper) {
        if (!isAdmin(userMapper)) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(), ResultCode.ACCESS_UNAUTHORIZED.getMsg());
        }
    }
}
```

- [ ] **步骤 2：命令/查询 DTO**

```java
package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/更新知识文章命令 DTO
 */
@Data
public class KnowledgeArticleSaveCommandDTO {

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String title;

    @Size(max = 1000, message = "摘要长度不能超过1000个字符")
    private String summary;

    @NotBlank(message = "正文不能为空")
    @Size(max = 100000, message = "正文长度超限")
    private String content;

    @Size(max = 255, message = "封面路径长度不能超过255个字符")
    private String coverImage;

    @Size(max = 500, message = "标签长度不能超过500个字符")
    private String tags;
}
```

```java
package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章状态变更 DTO（1发布 / 2下线）
 */
@Data
public class ArticleStatusChangeDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态仅支持 1发布/2下线")
    @Max(value = 2, message = "状态仅支持 1发布/2下线")
    private Integer status;
}
```

```java
package com.ai.aiproject.dto.query;

import lombok.Data;

/**
 * 知识文章分页查询 DTO（管理员与用户共用入口，按角色分流）
 */
@Data
public class KnowledgeArticlePageQueryDTO {

    private int currentPage = 1;

    private int size = 10;

    /** 管理员筛选：标题模糊 */
    private String title;

    /** 管理员筛选：分类ID */
    private Long categoryId;

    /** 管理员筛选：状态 '0'|'1'|'2'（字符串） */
    private String status;

    /** 用户排序字段：publishedAt | readCount */
    private String sortField;

    /** 用户排序方向：asc | desc */
    private String sortDirection;
}
```

- [ ] **步骤 3：响应 VO**

```java
package com.ai.aiproject.dto.response;

import lombok.Data;

/**
 * 分类树节点 VO（前端仅消费顶层 id/categoryName）
 */
@Data
public class KnowledgeCategoryVO {

    private Long id;

    private String categoryName;
}
```

```java
package com.ai.aiproject.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识文章分页行 VO
 */
@Data
public class KnowledgeArticlePageItemVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String title;

    private String summary;

    private String coverImage;

    private String authorName;

    private Integer readCount;

    private Integer status;

    private LocalDateTime updatedAt;
}
```

```java
package com.ai.aiproject.dto.response;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 知识文章详情 VO（详情页 + 管理端编辑回显共用）
 */
@Data
public class KnowledgeArticleDetailVO {

    private Long id;

    private Long categoryId;

    private String categoryName;

    private String title;

    private String summary;

    private String content;

    private String coverImage;

    /** 标签逗号串 */
    private String tags;

    /** 标签数组（前端多选/详情页消费） */
    private List<String> tagArray;

    private String authorName;

    private Integer readCount;

    private Integer status;

    private LocalDateTime publishedAt;

    private LocalDateTime updatedAt;
}
```

- [ ] **步骤 4：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test -Dtest=OssFileToolTest`
预期：PASS（BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/Utils/AuthzTool.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/command/KnowledgeArticleSaveCommandDTO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/command/ArticleStatusChangeDTO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/query/KnowledgeArticlePageQueryDTO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/KnowledgeCategoryVO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/KnowledgeArticlePageItemVO.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/dto/response/KnowledgeArticleDetailVO.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(knowledge): 管理员鉴权工具与知识库 DTO/VO"
```

---

### 任务 5：Service（分类树 + 文章 CRUD/双模式分页/详情计数）+ Controller

**文件：**
- 创建：`service/KnowledgeCategoryService.java`、`service/Impl/KnowledgeCategoryServiceImpl.java`
- 创建：`service/KnowledgeArticleService.java`、`service/Impl/KnowledgeArticleServiceImpl.java`
- 创建：`controller/KnowledgeController.java`

- [ ] **步骤 1：分类 Service 接口与实现**

```java
package com.ai.aiproject.service;

import com.ai.aiproject.dto.response.KnowledgeCategoryVO;

import java.util.List;

/**
 * 知识分类服务
 */
public interface KnowledgeCategoryService {

    /** 分类树（本期返回顶层扁平列表 [{id, categoryName}]） */
    List<KnowledgeCategoryVO> tree();
}
```

```java
package com.ai.aiproject.service.Impl;

import com.ai.aiproject.dto.response.KnowledgeCategoryVO;
import com.ai.aiproject.entity.KnowledgeCategory;
import com.ai.aiproject.mapper.KnowledgeCategoryMapper;
import com.ai.aiproject.service.KnowledgeCategoryService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeCategoryServiceImpl implements KnowledgeCategoryService {

    private final KnowledgeCategoryMapper knowledgeCategoryMapper;

    @Override
    public List<KnowledgeCategoryVO> tree() {
        return knowledgeCategoryMapper.selectList(
                        Wrappers.<KnowledgeCategory>lambdaQuery()
                                .orderByAsc(KnowledgeCategory::getSortOrder)
                                .orderByAsc(KnowledgeCategory::getId))
                .stream()
                .filter(c -> c.getParentId() == null || c.getParentId() == 0L)
                .map(c -> {
                    KnowledgeCategoryVO vo = new KnowledgeCategoryVO();
                    vo.setId(c.getId());
                    vo.setCategoryName(c.getCategoryName());
                    return vo;
                })
                .collect(Collectors.toList());
    }
}
```

- [ ] **步骤 2：文章 Service 接口**

```java
package com.ai.aiproject.service;

import com.ai.aiproject.dto.command.KnowledgeArticleSaveCommandDTO;
import com.ai.aiproject.dto.query.KnowledgeArticlePageQueryDTO;
import com.ai.aiproject.dto.response.KnowledgeArticleDetailVO;
import com.ai.aiproject.dto.response.KnowledgeArticlePageItemVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 知识文章服务（分页/详情按角色分流，写操作需管理员）
 */
public interface KnowledgeArticleService {

    Page<KnowledgeArticlePageItemVO> page(KnowledgeArticlePageQueryDTO queryDTO);

    KnowledgeArticleDetailVO detail(Long id);

    void create(KnowledgeArticleSaveCommandDTO commandDTO);

    void update(Long id, KnowledgeArticleSaveCommandDTO commandDTO);

    void changeStatus(Long id, Integer status);

    void delete(Long id);
}
```

- [ ] **步骤 3：文章 Service 实现**

```java
package com.ai.aiproject.service.Impl;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.AuthzTool;
import com.ai.aiproject.dto.command.KnowledgeArticleSaveCommandDTO;
import com.ai.aiproject.dto.query.KnowledgeArticlePageQueryDTO;
import com.ai.aiproject.dto.response.KnowledgeArticleDetailVO;
import com.ai.aiproject.dto.response.KnowledgeArticlePageItemVO;
import com.ai.aiproject.entity.KnowledgeArticle;
import com.ai.aiproject.entity.KnowledgeCategory;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.mapper.KnowledgeArticleMapper;
import com.ai.aiproject.mapper.KnowledgeCategoryMapper;
import com.ai.aiproject.mapper.UserMapper;
import com.ai.aiproject.service.KnowledgeArticleService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KnowledgeArticleServiceImpl implements KnowledgeArticleService {

    private final KnowledgeArticleMapper knowledgeArticleMapper;
    private final KnowledgeCategoryMapper knowledgeCategoryMapper;
    private final UserMapper userMapper;

    @Override
    public Page<KnowledgeArticlePageItemVO> page(KnowledgeArticlePageQueryDTO queryDTO) {
        boolean admin = AuthzTool.isAdmin(userMapper);
        int current = Math.max(1, queryDTO.getCurrentPage());
        int size = Math.max(1, queryDTO.getSize());

        LambdaQueryWrapper<KnowledgeArticle> wrapper = Wrappers.<KnowledgeArticle>lambdaQuery();
        if (admin) {
            if (queryDTO.getTitle() != null && !queryDTO.getTitle().isBlank()) {
                wrapper.like(KnowledgeArticle::getTitle, queryDTO.getTitle().trim());
            }
            if (queryDTO.getCategoryId() != null) {
                wrapper.eq(KnowledgeArticle::getCategoryId, queryDTO.getCategoryId());
            }
            if (queryDTO.getStatus() != null && !queryDTO.getStatus().isBlank()) {
                wrapper.eq(KnowledgeArticle::getStatus, parseStatus(queryDTO.getStatus()));
            }
            wrapper.orderByDesc(KnowledgeArticle::getUpdatedAt).orderByDesc(KnowledgeArticle::getId);
        } else {
            // 普通用户：只出已发布，支持 publishedAt/readCount 排序
            wrapper.eq(KnowledgeArticle::getStatus, 1);
            String sortField = queryDTO.getSortField();
            boolean asc = "asc".equalsIgnoreCase(queryDTO.getSortDirection());
            if ("readCount".equals(sortField)) {
                wrapper.orderBy(true, asc, KnowledgeArticle::getReadCount);
            } else if (sortField != null && !sortField.isBlank() && !"publishedAt".equals(sortField)) {
                throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的排序字段");
            } else {
                wrapper.orderBy(true, asc, KnowledgeArticle::getPublishedAt);
            }
            wrapper.orderByDesc(KnowledgeArticle::getId);
        }

        Page<KnowledgeArticle> articlePage = knowledgeArticleMapper.selectPage(new Page<>(current, size), wrapper);

        List<Long> categoryIds = articlePage.getRecords().stream()
                .map(KnowledgeArticle::getCategoryId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, KnowledgeCategory> categoryMap = categoryIds.isEmpty() ? Collections.emptyMap()
                : knowledgeCategoryMapper.selectBatchIds(categoryIds).stream()
                        .collect(Collectors.toMap(KnowledgeCategory::getId, c -> c));
        List<Long> authorIds = articlePage.getRecords().stream()
                .map(KnowledgeArticle::getAuthorId).filter(Objects::nonNull).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = authorIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        Page<KnowledgeArticlePageItemVO> result = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());
        result.setRecords(articlePage.getRecords().stream().map(a -> {
            KnowledgeArticlePageItemVO vo = new KnowledgeArticlePageItemVO();
            vo.setId(a.getId());
            vo.setCategoryId(a.getCategoryId());
            KnowledgeCategory cat = categoryMap.get(a.getCategoryId());
            if (cat != null) {
                vo.setCategoryName(cat.getCategoryName());
            }
            vo.setTitle(a.getTitle());
            vo.setSummary(a.getSummary());
            vo.setCoverImage(a.getCoverImage());
            User author = userMap.get(a.getAuthorId());
            vo.setAuthorName(author == null ? null : author.getDisplayName());
            vo.setReadCount(a.getReadCount());
            vo.setStatus(a.getStatus());
            vo.setUpdatedAt(a.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public KnowledgeArticleDetailVO detail(Long id) {
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        boolean admin = AuthzTool.isAdmin(userMapper);
        if (article == null || (!admin && article.getStatus() != 1)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "文章不存在或已下线");
        }
        // 普通用户阅读 +1
        if (!admin && article.getStatus() == 1) {
            knowledgeArticleMapper.update(null, Wrappers.<KnowledgeArticle>lambdaUpdate()
                    .eq(KnowledgeArticle::getId, id)
                    .setSql("read_count = read_count + 1"));
            article.setReadCount(article.getReadCount() == null ? 1 : article.getReadCount() + 1);
        }

        KnowledgeArticleDetailVO vo = new KnowledgeArticleDetailVO();
        vo.setId(article.getId());
        vo.setCategoryId(article.getCategoryId());
        KnowledgeCategory cat = knowledgeCategoryMapper.selectById(article.getCategoryId());
        if (cat != null) {
            vo.setCategoryName(cat.getCategoryName());
        }
        vo.setTitle(article.getTitle());
        vo.setSummary(article.getSummary());
        vo.setContent(article.getContent());
        vo.setCoverImage(article.getCoverImage());
        vo.setTags(article.getTags());
        vo.setTagArray(splitTags(article.getTags()));
        User author = userMapper.selectById(article.getAuthorId());
        vo.setAuthorName(author == null ? null : author.getDisplayName());
        vo.setReadCount(article.getReadCount());
        vo.setStatus(article.getStatus());
        vo.setPublishedAt(article.getPublishedAt());
        vo.setUpdatedAt(article.getUpdatedAt());
        return vo;
    }

    @Override
    public void create(KnowledgeArticleSaveCommandDTO commandDTO) {
        AuthzTool.requireAdmin(userMapper);
        requireCategoryExists(commandDTO.getCategoryId());
        LocalDateTime now = LocalDateTime.now();
        KnowledgeArticle article = KnowledgeArticle.builder()
                .categoryId(commandDTO.getCategoryId())
                .title(commandDTO.getTitle())
                .summary(commandDTO.getSummary())
                .content(commandDTO.getContent())
                .coverImage(commandDTO.getCoverImage())
                .tags(commandDTO.getTags())
                .authorId(com.ai.aiproject.Utils.SecurityContextTool.getCurrentUserId())
                .readCount(0)
                .status(0)
                .createdAt(now)
                .updatedAt(now)
                .build();
        knowledgeArticleMapper.insert(article);
    }

    @Override
    public void update(Long id, KnowledgeArticleSaveCommandDTO commandDTO) {
        AuthzTool.requireAdmin(userMapper);
        requireCategoryExists(commandDTO.getCategoryId());
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "文章不存在");
        }
        article.setCategoryId(commandDTO.getCategoryId());
        article.setTitle(commandDTO.getTitle());
        article.setSummary(commandDTO.getSummary());
        article.setContent(commandDTO.getContent());
        article.setCoverImage(commandDTO.getCoverImage());
        article.setTags(commandDTO.getTags());
        article.setUpdatedAt(LocalDateTime.now());
        knowledgeArticleMapper.updateById(article);
    }

    @Override
    public void changeStatus(Long id, Integer status) {
        AuthzTool.requireAdmin(userMapper);
        KnowledgeArticle article = knowledgeArticleMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "文章不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        article.setStatus(status);
        if (status == 1 && article.getPublishedAt() == null) {
            article.setPublishedAt(now);
        }
        article.setUpdatedAt(now);
        knowledgeArticleMapper.updateById(article);
    }

    @Override
    public void delete(Long id) {
        AuthzTool.requireAdmin(userMapper);
        knowledgeArticleMapper.deleteById(id);
    }

    private void requireCategoryExists(Long categoryId) {
        if (categoryId == null || knowledgeCategoryMapper.selectById(categoryId) == null) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "分类不存在");
        }
    }

    private Integer parseStatus(String status) {
        try {
            return Integer.valueOf(status.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的文章状态");
        }
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return new ArrayList<>();
        }
        List<String> list = new ArrayList<>();
        for (String tag : tags.split(",")) {
            String t = tag.trim();
            if (!t.isEmpty()) {
                list.add(t);
            }
        }
        return list;
    }
}
```

> 注：上述实现中 `parseStatus` 额外校验 0/1/2（parse 后仅接受 0-2），如需要可补强：`int s = Integer.parseInt(...); if (s < 0 || s > 2) throw ...`（任务 5 验证时确认行为与前端一致即可，0/1/2 均已覆盖）。

- [ ] **步骤 4：KnowledgeController**

```java
package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.command.ArticleStatusChangeDTO;
import com.ai.aiproject.dto.command.KnowledgeArticleSaveCommandDTO;
import com.ai.aiproject.dto.query.KnowledgeArticlePageQueryDTO;
import com.ai.aiproject.dto.response.KnowledgeArticleDetailVO;
import com.ai.aiproject.dto.response.KnowledgeArticlePageItemVO;
import com.ai.aiproject.dto.response.KnowledgeCategoryVO;
import com.ai.aiproject.service.KnowledgeArticleService;
import com.ai.aiproject.service.KnowledgeCategoryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库控制器
 * <p>
 * 前缀 /api/knowledge
 */
@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    @Resource
    private KnowledgeCategoryService knowledgeCategoryService;

    @Resource
    private KnowledgeArticleService knowledgeArticleService;

    @GetMapping("/category/tree")
    public Result<List<KnowledgeCategoryVO>> categoryTree() {
        return Result.ok(knowledgeCategoryService.tree());
    }

    @GetMapping("/article/page")
    public Result<Page<KnowledgeArticlePageItemVO>> articlePage(KnowledgeArticlePageQueryDTO queryDTO) {
        return Result.ok(knowledgeArticleService.page(queryDTO));
    }

    @GetMapping("/article/{id}")
    public Result<KnowledgeArticleDetailVO> articleDetail(@PathVariable Long id) {
        return Result.ok(knowledgeArticleService.detail(id));
    }

    @PostMapping("/article")
    public Result<Void> createArticle(@Valid @RequestBody KnowledgeArticleSaveCommandDTO commandDTO) {
        knowledgeArticleService.create(commandDTO);
        return Result.ok();
    }

    @PutMapping("/article/{id}")
    public Result<Void> updateArticle(@PathVariable Long id, @Valid @RequestBody KnowledgeArticleSaveCommandDTO commandDTO) {
        knowledgeArticleService.update(id, commandDTO);
        return Result.ok();
    }

    @PutMapping("/article/{id}/status")
    public Result<Void> changeArticleStatus(@PathVariable Long id, @Valid @RequestBody ArticleStatusChangeDTO statusDTO) {
        knowledgeArticleService.changeStatus(id, statusDTO.getStatus());
        return Result.ok();
    }

    @DeleteMapping("/article/{id}")
    public Result<Void> deleteArticle(@PathVariable Long id) {
        knowledgeArticleService.delete(id);
        return Result.ok();
    }
}
```

- [ ] **步骤 5：编译验证 + Commit**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=OssFileToolTest,TokenBlacklistTest,EmotionDiaryToolTest"`
预期：PASS（11/11，BUILD SUCCESS）

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/src/main/java/com/ai/aiproject/service/KnowledgeCategoryService.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/KnowledgeCategoryServiceImpl.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/KnowledgeArticleService.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/service/Impl/KnowledgeArticleServiceImpl.java" "backendCode/ai-project/src/main/java/com/ai/aiproject/controller/KnowledgeController.java"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "feat(knowledge): 知识库 Service 与 Controller（分类树/文章双模式分页/详情/CRUD/状态）"
```

---

### 任务 6：前端图片前缀小改 + 构建

**文件：**
- 修改：`frontendCode/ai-vue/src/config/index.js`
- 修改：`frontendCode/ai-vue/src/views/frontendKnowledge.vue`

- [ ] **步骤 1：config/index.js**

原：

```js
export const fileBaseUrl = 'http://159.75.169.224:1235'
```

改为：

```js
// OSS 公共读访问前缀（封面上传返回相对路径，前端拼接显示）
export const fileBaseUrl = 'https://psychology-ai.oss-cn-beijing.aliyuncs.com'
```

- [ ] **步骤 2：frontendKnowledge.vue**

① 脚本区 import（在第 66 行 `import { getKnowledgeList } from '@/api/frontend'` 后追加）：

```js
    import { fileBaseUrl } from '@/config/index.js'
```

② `getImage`（原第 98-100 行）改为：

```js
    const getImage = (url) => {
        return url ? fileBaseUrl + url : 'https://file.itndedu.com/psychology_ai.png'
    }
```

- [ ] **步骤 3：构建验证**

运行（在 `frontendCode/ai-vue`）：`npm run build`（如遇 EPERM，以 `danger-full-access` 重试同一条命令）
预期：`✓ built in ...`

另跑一次残留外网地址检查：`Select-String -Path "src\**\*.vue","src\**\*.js" -Pattern "159.75.169.224" | Select-Object -First 5`（应无命中，或仅剩注释）

- [ ] **步骤 4：Commit**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "frontendCode/ai-vue/src/config/index.js" "frontendCode/ai-vue/src/views/frontendKnowledge.vue"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "fix(frontend): 图片前缀统一指向 OSS 公共读域名"
```

---

### 任务 7：文档更新 + 整体回归

**文件：**
- 修改：`backendCode/ai-project/CLAUDE.md`
- 修改：`docs/接口缺口清单.md`

- [ ] **步骤 1：CLAUDE.md**

①「已实现接口」表中“其余 | 知识库/文件/分析 | ❌ 未实现”替换为：

```markdown
| 知识 | `GET /api/knowledge/category/tree` | ✅ 分类树（扁平 [{id, categoryName}]） |
| 知识 | `GET /api/knowledge/article/page` | ✅ 双模式分页：管理员可筛选 title/categoryId/status('0'/'1'/'2')；普通用户强制 status=1 并按 publishedAt/readCount 排序；返回 Page{records,total} 含 categoryName/authorName |
| 知识 | `GET /api/knowledge/article/{id}` | ✅ 详情：普通用户仅已发布且 read_count+1；管理员任意状态不计数；返回 tags/tagArray/content 等 |
| 知识 | `POST /api/knowledge/article` | ✅ 管理员新建（落草稿 0，author_id=当前管理员） |
| 知识 | `PUT /api/knowledge/article/{id}` | ✅ 管理员编辑（不改变 status/published_at） |
| 知识 | `PUT /api/knowledge/article/{id}/status` | ✅ 管理员发布(1)/下线(2)，发布时补 published_at |
| 知识 | `DELETE /api/knowledge/article/{id}` | ✅ 管理员删除（幂等） |
| 文件 | `POST /api/file/upload` | ✅ 管理员上传图片到 OSS（公共读；≤5MB；返回 {filePath}，前端拼 url-prefix） |
| 其余 | 数据分析 | ❌ 未实现 |
```

② 目录结构树在情绪日记相关行后追加（或就近插入）：

```text
├── entity/KnowledgeCategory.java、KnowledgeArticle.java + mapper/×2
├── service/KnowledgeCategoryService(.Impl)、KnowledgeArticleService(.Impl)（双模式分页/阅读计数/管理员写）
├── controller/KnowledgeController.java、FileController.java
├── service/FileStorageService(.Impl)（OSS）、config/OssProperties、Utils/OssFileTool（文件校验/键生成，含单测）
├── Utils/AuthzTool.java               # isAdmin/requireAdmin
```

- [ ] **步骤 2：docs/接口缺口清单.md**

① §2.3（知识库 7 行）与 §2.4（文件 1 行）表头各加“状态”列并逐行标注 `✅ 2026-09-08 已实现`；
② §4 落地顺序里第 3 条后加注“已于 2026-09-08 完成”。

- [ ] **步骤 3：整体回归**

运行：`mvn -s "E:\Ai-Code\DeepSeek\.mvn-settings.xml" test "-Dtest=OssFileToolTest,TokenBlacklistTest,EmotionDiaryToolTest"`
预期：PASS（11/11，BUILD SUCCESS）

（可选运行时冒烟，需真实 Secret 环境变量 + Bucket 公共读已确认 + DDL 已执行）：
启动后端 → 管理员登录 → 上传封面（OSS URL 可访问）→ 发文章/发布 → 普通用户列表见已发布 → 详情阅读数 +1 → 下线后用户不可见。

- [ ] **步骤 4：Commit**

```bash
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" add "backendCode/ai-project/CLAUDE.md" "docs/接口缺口清单.md"
git -C "E:\Ai-Code\DeepSeek\心理健康Ai助手" commit -m "docs: 标注知识库与文件上传接口已实现"
```
