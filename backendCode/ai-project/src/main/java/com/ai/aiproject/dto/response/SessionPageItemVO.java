package com.ai.aiproject.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 会话分页行 VO（用户端与管理端字段并集）
 */
@Data
public class SessionPageItemVO {

    private Long id;

    private String sessionTitle;

    private LocalDateTime startedAt;

    private String lastMessageContent;

    private LocalDateTime lastMessageTime;

    private Integer messageCount;

    private Integer durationMinutes;

    private String userNickname;
}
