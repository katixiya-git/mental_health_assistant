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
