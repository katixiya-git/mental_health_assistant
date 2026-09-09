package com.ai.aiproject.service.Impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.EmotionDiaryTool;
import com.ai.aiproject.Utils.PromptManage;
import com.ai.aiproject.Utils.SecurityContextTool;
import com.ai.aiproject.dto.command.EmotionDiaryAddCommandDTO;
import com.ai.aiproject.dto.query.EmotionDiaryAdminPageQueryDTO;
import com.ai.aiproject.dto.response.EmotionDiaryAdminItemDTO;
import com.ai.aiproject.entity.EmotionDiary;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.enums.UserType;
import com.ai.aiproject.mapper.EmotionDiaryMapper;
import com.ai.aiproject.mapper.UserMapper;
import com.ai.aiproject.service.EmotionDiaryService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 情绪日记服务实现
 */
@Service
@RequiredArgsConstructor
public class EmotionDiaryServiceImpl implements EmotionDiaryService {

    private final EmotionDiaryMapper emotionDiaryMapper;
    private final UserMapper userMapper;
    private final OpenAiChatModel chatModel;

    @Override
    public void addDiary(EmotionDiaryAddCommandDTO commandDTO) {
        // 1. 当前用户
        Long userId = SecurityContextTool.getCurrentUserId();
        // 2. 主要情绪白名单校验（其余由 @Valid 校验）
        EmotionDiaryTool.validateDominantEmotion(commandDTO.getDominantEmotion());
        // 3. 组装实体
        LocalDateTime now = LocalDateTime.now();
        EmotionDiary diary = EmotionDiary.builder()
                .userId(userId)
                .diaryDate(commandDTO.getDiaryDate())
                .moodScore(commandDTO.getMoodScore())
                .dominantEmotion(commandDTO.getDominantEmotion())
                .emotionTriggers(commandDTO.getEmotionTriggers())
                .diaryContent(commandDTO.getDiaryContent())
                .sleepQuality(commandDTO.getSleepQuality())
                .stressLevel(commandDTO.getStressLevel())
                .aiEmotionAnalysis(null)
                .createdAt(now)
                .updatedAt(now)
                .build();
        // 4. best-effort AI 情绪分析（有正文才调用；失败静默降级，不阻塞保存）
        if (hasText(commandDTO.getDiaryContent()) || hasText(commandDTO.getEmotionTriggers())) {
            try {
                diary.setAiEmotionAnalysis(analyzeEmotion(diary));
            } catch (Exception e) {
                diary.setAiEmotionAnalysis(null);
            }
        }
        emotionDiaryMapper.insert(diary);
    }

    @Override
    public Page<EmotionDiaryAdminItemDTO> adminPage(EmotionDiaryAdminPageQueryDTO queryDTO) {
        requireAdmin();
        long current = queryDTO.getCurrent() > 0 ? queryDTO.getCurrent() : 1;
        long size = queryDTO.getSize() > 0 ? queryDTO.getSize() : 10;
        int[] range = EmotionDiaryTool.parseMoodScoreRange(queryDTO.getMoodScreRange());

        LambdaQueryWrapper<EmotionDiary> wrapper = Wrappers.<EmotionDiary>lambdaQuery()
                .eq(hasText(queryDTO.getUserId()), EmotionDiary::getUserId, toUserId(queryDTO.getUserId()))
                .between(range != null, EmotionDiary::getMoodScore,
                        range == null ? 0 : range[0], range == null ? 0 : range[1])
                .orderByDesc(EmotionDiary::getDiaryDate)
                .orderByDesc(EmotionDiary::getId);

        Page<EmotionDiary> diaryPage = emotionDiaryMapper.selectPage(new Page<>(current, size), wrapper);

        // 用户信息批量补全（username/nickname）
        List<Long> userIds = diaryPage.getRecords().stream()
                .map(EmotionDiary::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        Page<EmotionDiaryAdminItemDTO> result = new Page<>(diaryPage.getCurrent(), diaryPage.getSize(), diaryPage.getTotal());
        result.setRecords(diaryPage.getRecords().stream().map(d -> {
            EmotionDiaryAdminItemDTO vo = new EmotionDiaryAdminItemDTO();
            vo.setId(d.getId());
            vo.setUserId(d.getUserId());
            User u = userMap.get(d.getUserId());
            if (u != null) {
                vo.setUsername(u.getUsername());
                vo.setNickname(u.getNickname());
            }
            vo.setDiaryDate(d.getDiaryDate());
            vo.setMoodScore(d.getMoodScore());
            vo.setDominantEmotion(d.getDominantEmotion());
            vo.setSleepQuality(d.getSleepQuality());
            vo.setStressLevel(d.getStressLevel());
            vo.setEmotionTriggers(d.getEmotionTriggers());
            vo.setDiaryContent(d.getDiaryContent());
            vo.setAiEmotionAnalysis(d.getAiEmotionAnalysis());
            vo.setCreatedAt(d.getCreatedAt());
            vo.setUpdatedAt(d.getUpdatedAt());
            return vo;
        }).collect(Collectors.toList()));
        return result;
    }

    @Override
    public void adminDelete(Long id) {
        requireAdmin();
        if (id != null) {
            emotionDiaryMapper.deleteById(id);
        }
    }

    /**
     * 调用 qwen 对日记做情绪分析，返回规范化 JSON 字符串
     */
    private String analyzeEmotion(EmotionDiary diary) {
        String userInput = "情绪评分(1-10)：" + diary.getMoodScore()
                + "\n主要情绪：" + (diary.getDominantEmotion() == null ? "未填写" : diary.getDominantEmotion())
                + "\n睡眠质量(1-5)：" + (diary.getSleepQuality() == null ? "未填写" : diary.getSleepQuality())
                + "\n压力水平(1-5)：" + (diary.getStressLevel() == null ? "未填写" : diary.getStressLevel())
                + "\n情绪触发因素：" + (diary.getEmotionTriggers() == null ? "未填写" : diary.getEmotionTriggers())
                + "\n日记内容：" + (diary.getDiaryContent() == null ? "未填写" : diary.getDiaryContent());
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PromptManage.DIARY_EMOTION_ANALYSIS_SYSTEM_PROMPT),
                new UserMessage(userInput)));
        var response = chatModel.call(prompt);
        String raw = response.getResult().getOutput().getContent();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        // 容错：剥离可能的 ```json ... ``` 包裹
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
        }
        JSONObject obj = JSONUtil.parseObj(json);
        // 规范化：字段齐全 + 数值夹取 + 数组兜底，保证与前端 JSON.parse 消费一致
        int score = obj.getInt("emotionScore", 0);
        score = Math.max(0, Math.min(100, score));
        int risk = obj.getInt("riskLevel", 0);
        risk = Math.max(0, Math.min(3, risk));
        JSONArray improvements = obj.getJSONArray("improvementSuggestions");
        if (improvements == null) {
            improvements = new JSONArray();
        }
        return JSONUtil.createObj()
                .set("primaryEmotion", obj.getStr("primaryEmotion", ""))
                .set("emotionScore", score)
                .set("isNegative", Boolean.TRUE.equals(obj.getBool("isNegative")))
                .set("riskLevel", risk)
                .set("suggestion", obj.getStr("suggestion", ""))
                .set("riskDescription", obj.getStr("riskDescription", ""))
                .set("improvementSuggestions", improvements)
                .toString();
    }

    /**
     * 校验当前登录用户为管理员，否则抛 A0301
     */
    private void requireAdmin() {
        Long userId = SecurityContextTool.getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null || !UserType.ADMIN.getCode().equals(user.getUserType())) {
            throw new BusinessException(ResultCode.ACCESS_UNAUTHORIZED.getCode(), ResultCode.ACCESS_UNAUTHORIZED.getMsg());
        }
    }

    private Long toUserId(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(userId.trim());
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "无效的用户ID");
        }
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
