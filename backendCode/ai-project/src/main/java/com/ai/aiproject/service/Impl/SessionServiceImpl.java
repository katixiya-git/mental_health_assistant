package com.ai.aiproject.service.Impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.AuthzTool;
import com.ai.aiproject.Utils.PromptManage;
import com.ai.aiproject.Utils.SecurityContextTool;
import com.ai.aiproject.dto.ConsultationSessionCreateDTO;
import com.ai.aiproject.dto.query.SessionPageQueryDTO;
import com.ai.aiproject.dto.response.ConsultationMessageResponseDTO;
import com.ai.aiproject.dto.response.SessionEmotionVO;
import com.ai.aiproject.dto.response.SessionPageItemVO;
import com.ai.aiproject.dto.response.StructOutPutResponseDTO;
import com.ai.aiproject.entity.ConsultationMessage;
import com.ai.aiproject.entity.ConsultationSession;
import com.ai.aiproject.entity.User;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.mapper.ConsultationMessageMapper;
import com.ai.aiproject.mapper.ConsultationSessionMapper;
import com.ai.aiproject.mapper.UserMapper;
import com.ai.aiproject.service.SessionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final ConsultationMessageMapper consultationMessageMapper;
    private final ConsultationSessionMapper consultationSessionMapper;
    private final OpenAiChatModel chatModel;
    private final ChatMemory chatMemory;
    private final UserMapper userMapper;

    @Value("${spring.ai.openai.chat.options.model:qwen-plus}")
    private String aiModel;

    @Transactional
    @Override
    public StructOutPutResponseDTO.StreamChatSession createSession(ConsultationSessionCreateDTO createDTO) {
        // 1. 获取当前用户 id（JWT 过滤器已写入 SecurityContext）
        Long userId = SecurityContextTool.getCurrentUserId();

        // 2. 标题为空时使用默认标题
        String title = (createDTO.getSessionTitle() == null || createDTO.getSessionTitle().isBlank())
                ? "未声明标题" : createDTO.getSessionTitle();

        // 3. 构建并保存 ConsultationSession（含 user_id）
        LocalDateTime now = LocalDateTime.now();
        ConsultationSession session = ConsultationSession.builder()
                .userId(userId)
                .sessionTitle(title)
                .startedAt(now)
                .build();
        consultationSessionMapper.insert(session);   // 回填 session.id

        // 4. 将 initialMessage 保存为第一条会话消息（无 user_id）
        ConsultationMessage message = ConsultationMessage.builder()
                .sessionId(session.getId())
                .senderType(1)          // 1 用户 / 2 AI
                .messageType(1)         // 1 文本
                .content(createDTO.getInitialMessage())
                .createdAt(now)
                .build();
        consultationMessageMapper.insert(message);

        // 5. 构建响应
        long startTime = System.currentTimeMillis();
        long expiryTime = startTime + 7L * 24 * 60 * 60 * 1000;   // 7 天
        return new StructOutPutResponseDTO.StreamChatSession(
                session.getId().toString(),
                userId,
                createDTO.getInitialMessage(),
                startTime,
                expiryTime,
                1,            // messageCount：新会话含 1 条初始消息
                "ACTIVE");         // status
    }

    @Override
    public Flux<ServerSentEvent<String>> streamChat(String sessionId, String userMessage) {
        // 1. 获取当前用户 id
        Long userId;
        try {
            userId = SecurityContextTool.getCurrentUserId();
        } catch (BusinessException e) {
            return Flux.just(sseError(ResultCode.UNAUTHORIZED, null));
        }

        // 2. 校验会话归属
        Long sessionIdLong;
        try {
            sessionIdLong = parseSessionId(sessionId);
        } catch (NumberFormatException e) {
            return Flux.just(sseError(ResultCode.PARAM_INVALID, "sessionId格式不正确"));
        }
        ConsultationSession session = consultationSessionMapper.selectById(sessionIdLong);
        if (session == null || !session.getUserId().equals(userId)) {
            return Flux.just(sseError(ResultCode.BUSINESS_ERROR, "会话不存在或无权访问"));
        }

        // 3. 读取会话历史（多轮记忆，取最近 20 条，不含当前消息）
        List<Message> history = chatMemory.get(sessionIdLong.toString(), 20);

        // 4. 持久化用户消息
        consultationMessageMapper.insert(ConsultationMessage.builder()
                .sessionId(sessionIdLong)
                .senderType(1)          // 1 用户
                .messageType(1)         // 1 文本
                .content(userMessage)
                .createdAt(LocalDateTime.now())
                .build());

        // 5. 构建 Prompt（系统提示词 + 历史 + 当前用户消息）
        List<Message> promptMessages = new ArrayList<>();
        promptMessages.add(new SystemMessage(PromptManage.PSYCHOLOGICAL_SUPPORT_SYSTEM_PROMPT));
        promptMessages.addAll(history);
        promptMessages.add(new UserMessage(userMessage));
        Prompt prompt = new Prompt(promptMessages);

        // 5. 流式调用 + 转 SSE（data 为 JSON：{code:"200", data:{content}}，与前端 fetchEventSource 契约一致）+ 流结束时持久化 AI 回复
        StringBuilder aiReply = new StringBuilder();
        return chatModel.stream(prompt)
                .map(resp -> {
                    String content = resp.getResult().getOutput().getContent();
                    if (content == null || content.isBlank()) {
                        return null;   // 过滤空 chunk
                    }
                    aiReply.append(content);
                    return ServerSentEvent.<String>builder().event("message")
                            .data(JSONUtil.toJsonStr(JSONUtil.createObj()
                                    .set("code", ResultCode.SUCCESS.getCode())
                                    .set("data", JSONUtil.createObj().set("content", content))))
                            .build();
                })
                .filter(Objects::nonNull)
                .concatWith(Flux.just(ServerSentEvent.<String>builder().event("done").data("completed").build()))
                .doOnComplete(() -> consultationMessageMapper.insert(
                        ConsultationMessage.builder()
                                .sessionId(sessionIdLong)
                                .senderType(2)          // 2 AI
                                .messageType(1)
                                .content(aiReply.toString())
                                .aiModel(aiModel)
                                .createdAt(LocalDateTime.now())
                                .build()))
                .onErrorResume(e -> Flux.just(sseError(ResultCode.SYSTEM_ERROR, e.getMessage())));
    }

    @Override
    public List<ConsultationMessageResponseDTO> getMessages(String sessionId) {
        // 1. 当前用户 + 会话归属校验
        Long userId = SecurityContextTool.getCurrentUserId();
        Long sessionIdLong;
        try {
            sessionIdLong = parseSessionId(sessionId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "sessionId格式不正确");
        }
        ConsultationSession session = consultationSessionMapper.selectById(sessionIdLong);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "会话不存在或无权访问");
        }

        // 2. 查询该会话消息（按创建时间升序）
        List<ConsultationMessage> messages = consultationMessageMapper.selectList(
                Wrappers.<ConsultationMessage>lambdaQuery()
                        .eq(ConsultationMessage::getSessionId, sessionIdLong)
                        .orderByAsc(ConsultationMessage::getCreatedAt));

        // 3. 转换为响应 DTO
        return messages.stream().map(this::convertToResponseDTO).collect(Collectors.toList());
    }

    /**
     * 咨询消息实体 → 响应 DTO
     */
    private ConsultationMessageResponseDTO convertToResponseDTO(ConsultationMessage message) {
        if (message == null) {
            return null;
        }
        ConsultationMessageResponseDTO dto = new ConsultationMessageResponseDTO();
        dto.setId(message.getId());
        dto.setSessionId(message.getSessionId());
        dto.setSenderType(message.getSenderType());
        dto.setMessageType(message.getMessageType());
        dto.setContent(message.getContent());
        dto.setEmotionTag(message.getEmotionTag());
        dto.setAiModel(message.getAiModel());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setSenderTypeDesc(message.getSenderTypeDesc());
        dto.setMessageTypeDesc(message.getMessageTypeDesc());
        dto.calculateContentLength();
        return dto;
    }

    @Override
    public Page<SessionPageItemVO> pageSessions(SessionPageQueryDTO queryDTO) {
        Long userId = SecurityContextTool.getCurrentUserId();
        boolean admin = AuthzTool.isAdmin(userMapper);
        long current = queryDTO.getPageNum() > 0 ? queryDTO.getPageNum() : queryDTO.getCurrentPage();
        long size = queryDTO.getPageSize() > 0 ? queryDTO.getPageSize() : queryDTO.getSize();
        current = Math.max(1, current);
        size = Math.max(1, size);

        Page<ConsultationSession> sessionPage = consultationSessionMapper.selectPage(new Page<>(current, size),
                Wrappers.<ConsultationSession>lambdaQuery()
                        .eq(!admin, ConsultationSession::getUserId, userId)
                        .orderByDesc(ConsultationSession::getStartedAt)
                        .orderByDesc(ConsultationSession::getId));

        List<Long> ownerIds = sessionPage.getRecords().stream()
                .map(ConsultationSession::getUserId).distinct().collect(Collectors.toList());
        Map<Long, User> userMap = ownerIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(ownerIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        List<SessionPageItemVO> records = new ArrayList<>();
        for (ConsultationSession session : sessionPage.getRecords()) {
            SessionPageItemVO vo = new SessionPageItemVO();
            vo.setId(session.getId());
            vo.setSessionTitle(session.getSessionTitle());
            vo.setStartedAt(session.getStartedAt());
            User owner = userMap.get(session.getUserId());
            vo.setUserNickname(owner == null ? null : owner.getDisplayName());

            ConsultationMessage last = consultationMessageMapper.selectOne(
                    Wrappers.<ConsultationMessage>lambdaQuery()
                            .eq(ConsultationMessage::getSessionId, session.getId())
                            .orderByDesc(ConsultationMessage::getCreatedAt)
                            .orderByDesc(ConsultationMessage::getId)
                            .last("LIMIT 1"));
            long count = consultationMessageMapper.selectCount(
                    Wrappers.<ConsultationMessage>lambdaQuery()
                            .eq(ConsultationMessage::getSessionId, session.getId()));
            vo.setMessageCount((int) count);
            if (last != null) {
                vo.setLastMessageContent(last.getContent());
                vo.setLastMessageTime(last.getCreatedAt());
                vo.setDurationMinutes(calcDurationMinutes(session.getStartedAt(), last.getCreatedAt()));
            } else {
                vo.setLastMessageContent(null);
                vo.setLastMessageTime(null);
                vo.setDurationMinutes(0);
            }
            records.add(vo);
        }

        Page<SessionPageItemVO> result = new Page<>(sessionPage.getCurrent(), sessionPage.getSize(), sessionPage.getTotal());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public void deleteSession(String sessionId) {
        Long sessionIdLong = parseSessionIdQuiet(sessionId);
        ConsultationSession session = consultationSessionMapper.selectById(sessionIdLong);
        if (session == null) {
            return; // 幂等
        }
        Long userId = SecurityContextTool.getCurrentUserId();
        if (!AuthzTool.isAdmin(userMapper) && !userId.equals(session.getUserId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "会话不存在或无权访问");
        }
        consultationMessageMapper.delete(Wrappers.<ConsultationMessage>lambdaQuery()
                .eq(ConsultationMessage::getSessionId, sessionIdLong));
        consultationSessionMapper.deleteById(sessionIdLong);
    }

    @Override
    public SessionEmotionVO getSessionEmotion(String sessionId) {
        Long sessionIdLong = parseSessionIdQuiet(sessionId);
        ConsultationSession session = consultationSessionMapper.selectById(sessionIdLong);
        Long userId = SecurityContextTool.getCurrentUserId();
        if (session == null || (!AuthzTool.isAdmin(userMapper) && !userId.equals(session.getUserId()))) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR.getCode(), "会话不存在或无权访问");
        }

        ConsultationMessage last = consultationMessageMapper.selectOne(
                Wrappers.<ConsultationMessage>lambdaQuery()
                        .eq(ConsultationMessage::getSessionId, sessionIdLong)
                        .orderByDesc(ConsultationMessage::getCreatedAt)
                        .orderByDesc(ConsultationMessage::getId)
                        .last("LIMIT 1"));
        if (last == null) {
            return defaultEmotion();   // 无消息
        }
        // 策略 B：无新消息且已有缓存 → 直接返回缓存
        boolean upToDate = session.getLastEmotionUpdatedAt() != null
                && last.getCreatedAt() != null
                && !last.getCreatedAt().isAfter(session.getLastEmotionUpdatedAt());
        if (upToDate && hasText(session.getLastEmotionAnalysis())) {
            return parseStoredEmotion(session.getLastEmotionAnalysis());
        }
        // 有新消息 → 重算；失败降级：优先旧缓存，否则默认
        try {
            SessionEmotionVO vo = analyzeSessionEmotion(sessionIdLong);
            session.setLastEmotionAnalysis(JSONUtil.toJsonStr(vo));
            session.setLastEmotionUpdatedAt(LocalDateTime.now());
            consultationSessionMapper.updateById(session);
            return vo;
        } catch (Exception e) {
            if (hasText(session.getLastEmotionAnalysis())) {
                return parseStoredEmotion(session.getLastEmotionAnalysis());
            }
            return defaultEmotion();
        }
    }

    /**
     * 取最近 30 条消息（按时间正序拼接）调 qwen 分析
     */
    private SessionEmotionVO analyzeSessionEmotion(Long sessionIdLong) {
        List<ConsultationMessage> recent = consultationMessageMapper.selectList(
                Wrappers.<ConsultationMessage>lambdaQuery()
                        .eq(ConsultationMessage::getSessionId, sessionIdLong)
                        .orderByDesc(ConsultationMessage::getCreatedAt)
                        .orderByDesc(ConsultationMessage::getId)
                        .last("LIMIT 30"));
        if (recent.isEmpty()) {
            return defaultEmotion();
        }
        Collections.reverse(recent); // 转时间正序
        StringBuilder sb = new StringBuilder();
        for (ConsultationMessage m : recent) {
            if (m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            sb.append(Objects.equals(m.getSenderType(), 1) ? "用户：" : "AI：")
              .append(m.getContent()).append("\n");
        }
        if (sb.length() == 0) {
            return defaultEmotion();
        }
        Prompt prompt = new Prompt(List.of(
                new SystemMessage(PromptManage.SESSION_EMOTION_ANALYSIS_SYSTEM_PROMPT),
                new UserMessage("最近对话记录：\n" + sb)));
        var response = chatModel.call(prompt);
        String raw = response.getResult().getOutput().getContent();
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("AI 情绪分析返回为空");
        }
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
        }
        cn.hutool.json.JSONObject obj = JSONUtil.parseObj(json);
        SessionEmotionVO vo = new SessionEmotionVO();
        vo.setPrimaryEmotion(defaultStr(obj.getStr("primaryEmotion"), "中性"));
        int score = obj.getInt("emotionScore", 0);
        vo.setEmotionScore(Math.max(0, Math.min(100, score)));
        vo.setIsNegative(Boolean.TRUE.equals(obj.getBool("isNegative")));
        int risk = obj.getInt("riskLevel", 0);
        vo.setRiskLevel(Math.max(0, Math.min(3, risk)));
        vo.setSuggestion(defaultStr(obj.getStr("suggestion"), ""));
        vo.setRiskDescription(defaultStr(obj.getStr("riskDescription"), ""));
        vo.setImprovementSuggestions(extractImprovements(obj.getJSONArray("improvementSuggestions")));
        return vo;
    }

    private SessionEmotionVO parseStoredEmotion(String storedJson) {
        try {
            cn.hutool.json.JSONObject obj = JSONUtil.parseObj(storedJson);
            SessionEmotionVO vo = new SessionEmotionVO();
            vo.setPrimaryEmotion(defaultStr(obj.getStr("primaryEmotion"), "中性"));
            int score = obj.getInt("emotionScore", 50);
            vo.setEmotionScore(Math.max(0, Math.min(100, score)));
            vo.setIsNegative(Boolean.TRUE.equals(obj.getBool("isNegative")));
            int risk = obj.getInt("riskLevel", 0);
            vo.setRiskLevel(Math.max(0, Math.min(3, risk)));
            vo.setSuggestion(defaultStr(obj.getStr("suggestion"), "情绪状态平稳"));
            vo.setRiskDescription(defaultStr(obj.getStr("riskDescription"), ""));
            vo.setImprovementSuggestions(extractImprovements(obj.getJSONArray("improvementSuggestions")));
            return vo;
        } catch (Exception e) {
            return defaultEmotion();
        }
    }

    private SessionEmotionVO defaultEmotion() {
        SessionEmotionVO vo = new SessionEmotionVO();
        vo.setPrimaryEmotion("中性");
        vo.setEmotionScore(50);
        vo.setIsNegative(false);
        vo.setRiskLevel(0);
        vo.setSuggestion("情绪状态平稳");
        vo.setImprovementSuggestions(new ArrayList<>());
        vo.setRiskDescription("");
        return vo;
    }

    private List<String> extractImprovements(cn.hutool.json.JSONArray improvements) {
        List<String> list = new ArrayList<>();
        if (improvements != null) {
            for (int i = 0; i < improvements.size(); i++) {
                String s = improvements.getStr(i);
                if (s != null && !s.isBlank()) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    private int calcDurationMinutes(LocalDateTime startedAt, LocalDateTime endAt) {
        if (startedAt == null || endAt == null) {
            return 0;
        }
        long minutes = ChronoUnit.MINUTES.between(startedAt, endAt);
        return (int) Math.max(0, minutes);
    }

    /** 会话 ID 解析，非法格式抛 PARAM_INVALID */
    private Long parseSessionIdQuiet(String sessionId) {
        try {
            return parseSessionId(sessionId);
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_INVALID.getCode(), "sessionId格式不正确");
        }
    }

    private String defaultStr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 解析会话 ID：兼容前端 "session_123" / "123" 两种格式
     *
     * @param sessionId 会话 ID（可为 "session_" 前缀）
     * @return 纯数字会话 ID
     * @throws NumberFormatException 格式不正确时抛出
     */
    private Long parseSessionId(String sessionId) {
        if (sessionId == null) {
            throw new NumberFormatException("sessionId为空");
        }
        String raw = sessionId.trim();
        if (raw.startsWith("session_")) {
            raw = raw.substring("session_".length());
        }
        return Long.valueOf(raw);
    }

    /**
     * 构造 SSE 错误事件（data 为 JSON：{code, message}，前端取 payload.message 展示）
     */
    private static ServerSentEvent<String> sseError(ResultCode code, String msg) {
        return ServerSentEvent.<String>builder().event("error")
                .data(JSONUtil.toJsonStr(JSONUtil.createObj()
                        .set("code", code.getCode())
                        .set("message", msg != null ? msg : code.getMsg())))
                .build();
    }
}
