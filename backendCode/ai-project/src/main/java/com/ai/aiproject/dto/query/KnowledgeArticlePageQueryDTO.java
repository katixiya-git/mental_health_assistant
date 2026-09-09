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
