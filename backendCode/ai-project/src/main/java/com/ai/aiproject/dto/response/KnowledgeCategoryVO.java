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
