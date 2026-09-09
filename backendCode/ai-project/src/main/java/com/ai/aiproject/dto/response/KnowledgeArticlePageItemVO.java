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
