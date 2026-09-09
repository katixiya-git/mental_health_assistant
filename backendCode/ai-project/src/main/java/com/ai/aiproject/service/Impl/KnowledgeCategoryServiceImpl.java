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
