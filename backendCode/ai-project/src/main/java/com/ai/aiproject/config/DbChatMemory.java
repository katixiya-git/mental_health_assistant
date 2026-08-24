package com.ai.aiproject.config;

import com.ai.aiproject.entity.ConsultationMessage;
import com.ai.aiproject.mapper.ConsultationMessageMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基于数据库的 ChatMemory 实现：conversationId 即咨询会话 sessionId
 * <p>
 * get：从 consultation_message 读历史（用户消息→UserMessage、AI 消息→AssistantMessage）
 * add：把消息写入 consultation_message
 * clear：删除该会话全部消息
 */
@Component
@RequiredArgsConstructor
public class DbChatMemory implements ChatMemory {

    private final ConsultationMessageMapper consultationMessageMapper;

    @Override
    public List<Message> get(String conversationId, int lastN) {
        List<ConsultationMessage> messages = consultationMessageMapper.selectList(
                Wrappers.<ConsultationMessage>lambdaQuery()
                        .eq(ConsultationMessage::getSessionId, Long.valueOf(conversationId))
                        .orderByDesc(ConsultationMessage::getCreatedAt)
                        .last("LIMIT " + lastN));
        // 倒序取的是最近 N 条，反转回时间升序
        Collections.reverse(messages);
        return messages.stream()
                .map(m -> (Message) (m.getSenderType() == 2
                        ? new AssistantMessage(m.getContent())
                        : new UserMessage(m.getContent())))
                .collect(Collectors.toList());
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        Long sessionId = Long.valueOf(conversationId);
        for (Message message : messages) {
            consultationMessageMapper.insert(ConsultationMessage.builder()
                    .sessionId(sessionId)
                    .senderType(message instanceof AssistantMessage ? 2 : 1)
                    .messageType(1)
                    .content(extractText(message))
                    .createdAt(LocalDateTime.now())
                    .build());
        }
    }

    @Override
    public void clear(String conversationId) {
        consultationMessageMapper.delete(Wrappers.<ConsultationMessage>lambdaQuery()
                .eq(ConsultationMessage::getSessionId, Long.valueOf(conversationId)));
    }

    private String extractText(Message message) {
        if (message instanceof UserMessage userMessage) {
            return userMessage.getText();
        }
        if (message instanceof AssistantMessage assistantMessage) {
            return assistantMessage.getText();
        }
        if (message instanceof SystemMessage systemMessage) {
            return systemMessage.getText();
        }
        return null;
    }
}
