package com.ai.aiproject.service;

import com.ai.aiproject.dto.command.EmotionDiaryAddCommandDTO;
import com.ai.aiproject.dto.query.EmotionDiaryAdminPageQueryDTO;
import com.ai.aiproject.dto.response.EmotionDiaryAdminItemDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 情绪日记服务
 */
public interface EmotionDiaryService {

    /**
     * 用户新增日记（含 best-effort AI 情绪分析落库）
     */
    void addDiary(EmotionDiaryAddCommandDTO commandDTO);

    /**
     * 管理端分页查询（需管理员；含用户昵称/用户名补全）
     */
    Page<EmotionDiaryAdminItemDTO> adminPage(EmotionDiaryAdminPageQueryDTO queryDTO);

    /**
     * 管理端删除（幂等，需管理员）
     */
    void adminDelete(Long id);
}
