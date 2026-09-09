# 知识库模块设计规格（含 OSS 封面上传）

> 日期：2026-09-08
> 范围：`backendCode/ai-project`（8 个新接口 + OSS 集成 + 2 张新表）+ `frontendCode/ai-vue`（图片地址 2 处小改）
> 关联：`docs/接口缺口清单.md`（缺口 #7-#14）、`docs/前端调用契约报告.md` §五/§六

## 1. 背景与目标

知识库三块前端页面（前台列表/详情、后台文章管理含封面上传）调用 8 个接口全部 404。目标：补齐接口，使知识库全链路可用；封面经**阿里云 OSS** 存取。

## 2. 已确认决策

- **OSS**：Bucket `psychology-ai`（华北2/北京，`oss-cn-beijing.aliyuncs.com`），**公共读**；图片访问前缀 = OSS 默认域名 `https://psychology-ai.oss-cn-beijing.aliyuncs.com`。AccessKey 走环境变量（`OSS_ACCESS_KEY_ID/SECRET`），不落 git。
- **分类**：单层 + 种子 5 类（心理健康/情绪管理/压力应对/人际交往/自我成长），`parent_id` 预留层级，本期不做分类管理接口。
- **建表**：授权实施时尝试直接执行（本机 MySQL `root/123456`），失败转手动。
- **上传**：`POST /file/upload` 管理员使用；multipart 字段 `file/businessType/businessId/businessField`（后三仅按契约接收，本期不建文件表、不落库关联）；仅支持图片（jpg/jpeg/png/gif/webp）、≤5MB；文件名 `uuid.ext`，目录 `article/cover/yyyyMM/`；返回 `{filePath:'/article/cover/yyyyMM/uuid.ext'}`。

## 3. 接口契约（相对 /api；除标注外均需登录）

| # | 接口 | 行为 |
|---|---|---|
| 1 | `GET /knowledge/category/tree` | 返回扁平数组 `[{id, categoryName}]`（顶层节点按 sort_order,id 排序） |
| 2 | `GET /knowledge/article/page` | **管理员**：`currentPage/size + title(模糊)/categoryId/status('0'/'1'/'2' 字符串)`，按 updatedAt desc；**普通用户**：强制 `status=1`，按 `sortField(publishedAt\|readCount)` + `sortDirection(asc\|desc)` 白名单排序，默认 publishedAt desc。返回 `Page{records,total}`；行 VO：`id,title,categoryId,categoryName,coverImage,summary,authorName,readCount,status,updatedAt`（categoryName/authorName 补全） |
| 3 | `GET /knowledge/article/{id}` | 普通用户仅可读 `status=1` 的文章（否则 BUSINESS_ERROR 文章不存在或已下线）且阅读数 +1；管理员可读任意状态不计数。详情 VO 另含 `content/tags(逗号串)/tagArray[]/publishedAt` |
| 4 | `POST /knowledge/article` | **管理员**：落草稿(0)，author_id=当前管理员，body 的 uuid `id` 忽略；校验 category 存在 |
| 5 | `PUT /knowledge/article/{id}` | **管理员**：仅更新 title/categoryId/summary/content/coverImage/tags（不改 status/published_at） |
| 6 | `PUT /knowledge/article/{id}/status` | **管理员**：body `{status: 1\|2}`；置 1 且 published_at 为空时写入当前时间 |
| 7 | `DELETE /knowledge/article/{id}` | **管理员**：幂等删除 |
| 8 | `POST /file/upload` | **管理员**：`file` 必填；按 §2 规则传 OSS，返回 `Result<FileUploadResponseDTO{filePath}>` |

错误码全部复用：PARAM_INVALID / BUSINESS_ERROR(6000) / A0301 / FILE_TYPE_NOT_SUPPORTED(5005) / FILE_SIZE_EXCEEDED(5004) / FILE_UPLOAD_FAILED(5002)。

## 4. 数据库（新表 2 张，DDL 见 docs/sql/2026-09-08-knowledge.sql）

- `knowledge_category(id PK, category_name VARCHAR(50) UNIQUE, parent_id BIGINT DEFAULT 0, sort_order INT DEFAULT 0, created_at, updated_at)` + 5 条种子（INSERT IGNORE）。
- `knowledge_article(id PK, category_id BIGINT NOT NULL, title VARCHAR(200), summary VARCHAR(1000), content LONGTEXT, cover_image VARCHAR(255), tags VARCHAR(500), author_id BIGINT, read_count INT DEFAULT 0, status TINYINT DEFAULT 0, published_at DATETIME NULL, created_at, updated_at)`，索引 `idx_cat(category_id)`、`idx_status_published(status,published_at)`。

## 5. 权限模型

复用 Service 层校验；抽公共 `Utils/AuthzTool`：
- `isAdmin(UserMapper)`：无登录上下文时返回 false（不抛）；
- `requireAdmin(UserMapper)`：非管理员抛 A0301（admin 专属写操作）。

## 6. 前端改动（~4 行）

- `frontendCode/ai-vue/src/config/index.js`：`fileBaseUrl = 'https://psychology-ai.oss-cn-beijing.aliyuncs.com'`。
- `frontendCode/ai-vue/src/views/frontendKnowledge.vue`：`getImage()` 中写死的 `'http://159.75.169.224:1235'` 改为引入并使用 `fileBaseUrl`（兜底图 URL 保留）。

## 7. 文件改动清单

| 操作 | 文件 |
|---|---|
| 新增 | `docs/sql/2026-09-08-knowledge.sql` |
| 新增 | `config/OssProperties.java`、`Utils/OssStorageTool.java`（+纯静态校验拆分）、`config/OssClientConfig`（若需要） |
| 新增 | `service/FileStorageService(.Impl)`、`controller/FileController.java`、`dto/response/FileUploadResponseDTO.java` |
| 新增 | `entity/KnowledgeCategory.java`、`KnowledgeArticle.java`、`mapper/…Mapper.java` ×2 |
| 新增 | `dto/command/KnowledgeArticleSaveCommandDTO.java`、`ArticleStatusChangeDTO.java`、`dto/query/KnowledgeArticlePageQueryDTO.java`、`dto/response/KnowledgeCategoryVO.java`、`KnowledgeArticlePageItemVO.java`、`KnowledgeArticleDetailVO.java` |
| 新增 | `service/KnowledgeCategoryService(.Impl)`、`KnowledgeArticleService(.Impl)`、`controller/KnowledgeController.java` |
| 新增 | `Utils/AuthzTool.java` |
| 修改 | `pom.xml`（+aliyun-sdk-oss）、`application.yaml`（+oss.*） |
| 修改 | `frontendCode/ai-vue/src/config/index.js`、`src/views/frontendKnowledge.vue` |
| 修改 | `CLAUDE.md`、`docs/接口缺口清单.md`（#7-#14 状态） |
| 新增 | 本规格 + `docs/superpowers/plans/2026-09-08-knowledge.md` |

## 8. 验证

- 编译 + 纯 JVM 单测回归（TokenBlacklist/EmotionDiaryTool + 上传路径/校验工具单测）；
- DDL 自动执行探测（本机 MySQL，MYSQL_PWD 方式）；
- 前端 `npm run build`；
- 运行时冒烟（可选，需真实 Secret/OSS）：上传→OSS URL 可访问、文章 CRUD/上下线/阅读数、用户端只读已发布；
- 不做：分类管理、审核流、标签独立表、文件表/业务关联、OSS 签名 URL、游客免登录浏览（前台“知识库”入口虽对未登录可见，读接口本期保持需登录，属已知限制）。

## 9. 已知限制/风险

- AccessKey ID 已在对话中出现，建议功能验收后到 RAM 轮换；Secret 永远只放环境变量。
- Bucket 需在控制台确认“公共读”，否则图片 403。
- 图片在 OSS 域名直出，不走本项目；前端两处前缀统一为 fileBaseUrl。
