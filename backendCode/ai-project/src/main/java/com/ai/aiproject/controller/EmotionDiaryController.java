package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.command.EmotionDiaryAddCommandDTO;
import com.ai.aiproject.dto.query.EmotionDiaryAdminPageQueryDTO;
import com.ai.aiproject.dto.response.EmotionDiaryAdminItemDTO;
import com.ai.aiproject.service.EmotionDiaryService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 情绪日记控制器
 * <p>
 * 前缀 /api/emotion-diary
 */
@RestController
@RequestMapping("/api/emotion-diary")
public class EmotionDiaryController {

    @Resource
    private EmotionDiaryService emotionDiaryService;

    /**
     * 用户新增日记
     * POST /api/emotion-diary
     */
    @PostMapping
    public Result<Void> add(@Valid @RequestBody EmotionDiaryAddCommandDTO commandDTO) {
        emotionDiaryService.addDiary(commandDTO);
        return Result.ok();
    }

    /**
     * 管理端分页查询（需管理员）
     * GET /api/emotion-diary/admin/page
     */
    @GetMapping("/admin/page")
    public Result<Page<EmotionDiaryAdminItemDTO>> adminPage(EmotionDiaryAdminPageQueryDTO queryDTO) {
        return Result.ok(emotionDiaryService.adminPage(queryDTO));
    }

    /**
     * 管理端删除（需管理员，幂等）
     * DELETE /api/emotion-diary/admin/{id}
     */
    @DeleteMapping("/admin/{id}")
    public Result<Void> adminDelete(@PathVariable Long id) {
        emotionDiaryService.adminDelete(id);
        return Result.ok();
    }
}
