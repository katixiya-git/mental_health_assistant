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
