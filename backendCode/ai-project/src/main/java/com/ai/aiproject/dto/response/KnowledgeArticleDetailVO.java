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
