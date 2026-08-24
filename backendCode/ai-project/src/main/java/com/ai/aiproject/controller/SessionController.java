package com.ai.aiproject.controller;

import cn.hutool.json.JSONUtil;
import com.ai.aiproject.Utils.SecurityContextTool;
import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.ConsultationSessionCreateDTO;
import com.ai.aiproject.dto.ConsultationStreamDTO;
import com.ai.aiproject.dto.response.ConsultationMessageResponseDTO;
import com.ai.aiproject.dto.response.StructOutPutResponseDTO;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.service.SessionService;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

@RestController
@RequestMapping("/api/psychological-chat")
public class SessionController {
    @Resource
    private SessionService sessionService;

    /**
     * 创建会话
     */
    @PostMapping("/session/start")
    public Result<StructOutPutResponseDTO.StreamChatSession> createSession(@Validated @RequestBody ConsultationSessionCreateDTO createDTO) {
        StructOutPutResponseDTO.StreamChatSession session = sessionService.createSession(createDTO);
        return Result.ok(session);
    }
    /**
     * 流式对话
     * <p>
     * SSE 协议：正常 chunk data 为 JSON {code:"200", data:{content}}，结束发 event:done（data 非空），错误发 event:error（data 为 {code, message}）
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(@Validated @RequestBody ConsultationStreamDTO streamDTO) {
        return sessionService.streamChat(streamDTO.getSessionId(), streamDTO.getUserMessage());
    }
    /**
     * 获取会话消息
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ConsultationMessageResponseDTO>> getMessages(@PathVariable String sessionId) {
        return Result.ok(sessionService.getMessages(sessionId));
    }
}
