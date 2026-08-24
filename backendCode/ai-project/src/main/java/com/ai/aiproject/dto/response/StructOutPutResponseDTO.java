package com.ai.aiproject.dto.response;

public class StructOutPutResponseDTO {
    public record StreamChatSession(
            String sessionId,
            Long userHash,
            String initialMessage,
            Long startTime,
            Long expiryTime,
            Integer messageCount,
            String status
    ) {}
}
