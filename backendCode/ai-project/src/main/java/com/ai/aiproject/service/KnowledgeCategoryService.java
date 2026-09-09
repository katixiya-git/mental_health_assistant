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
