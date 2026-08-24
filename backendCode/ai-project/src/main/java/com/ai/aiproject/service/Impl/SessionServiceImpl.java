package com.ai.aiproject.service.Impl;

import cn.hutool.json.JSONUtil;
import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.PromptManage;
import com.ai.aiproject.Utils.SecurityContextTool;
import com.ai.aiproject.dto.ConsultationSessionCreateDTO;
import com.ai.aiproject.dto.response.ConsultationMessageResponseDTO;
import com.ai.aiproject.dto.response.StructOutPutResponseDTO;
import com.ai.aiproject.entity.ConsultationMessage;
import com.ai.aiproject.entity.ConsultationSession;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.mapper.ConsultationMessageMapper;
import com.ai.aiproject.mapper.ConsultationSessionMapper;
import com.ai.aiproject.service.SessionService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SessionServiceImpl implements SessionService {

    private final ConsultationMessageMapper consultationMessageMapper;
    private final ConsultationSessionMapper consultationSessionMapper;
    private final OpenAiChatModel chatModel;
    private final ChatMemory chatMemory;

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
