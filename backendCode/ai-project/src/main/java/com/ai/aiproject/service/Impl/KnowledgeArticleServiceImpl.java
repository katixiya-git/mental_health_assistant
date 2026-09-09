package com.ai.aiproject.service.Impl;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.AuthzTool;
import com.ai.aiproject.Utils.SecurityContextTool;
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
            if (queryDTO.getSortDirection() != null && !queryDTO.getSortDirection().isBlank()
                    && !"asc".equalsIgnoreCase(queryDTO.getSortDirection())
                    && !"desc".equalsIgnoreCase(queryDTO.getSortDirection())) {
                throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的排序方向");
            }
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
                .authorId(SecurityContextTool.getCurrentUserId())
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

    /** 解析状态字符串 '0'|'1'|'2'，非法抛 PARAM_INVALID */
    private Integer parseStatus(String status) {
        int value;
        try {
            value = Integer.parseInt(status.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的文章状态");
        }
        if (value < 0 || value > 2) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的文章状态");
        }
        return value;
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
