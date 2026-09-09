package com.ai.aiproject.service;

import com.ai.aiproject.dto.ConsultationSessionCreateDTO;
import com.ai.aiproject.dto.query.SessionPageQueryDTO;
import com.ai.aiproject.dto.response.ConsultationMessageResponseDTO;
import com.ai.aiproject.dto.response.SessionEmotionVO;
import com.ai.aiproject.dto.response.SessionPageItemVO;
import com.ai.aiproject.dto.response.StructOutPutResponseDTO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

public interface SessionService {
    /**
     * 创建会话
     * @param createDTO 会话创建DTO
     * @return 会话
     */
    StructOutPutResponseDTO.StreamChatSession createSession(ConsultationSessionCreateDTO createDTO);

    Flux<ServerSentEvent<String>> streamChat(@NotBlank(message = "sessionId不能为空") String sessionId, @NotBlank(message = "初始消息不能为空") @Size(max = 2000, message = "初始消息长度不能超过2000个字符") String userMessage);

    /**
     * 获取会话消息列表
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ConsultationMessageResponseDTO> getMessages(String sessionId);

    /**
     * 会话分页列表（管理员看全部，普通用户看自己；兼容 pageNum/pageSize 与 currentPage/size）
     */
    Page<SessionPageItemVO> pageSessions(SessionPageQueryDTO queryDTO);

    /**
     * 删除会话（本人或管理员；级联删除消息；幂等）
     */
    void deleteSession(String sessionId);

    /**
     * 会话情绪分析（增量缓存：仅新消息触发重算；AI 失败降级返回缓存/默认）
     */
    SessionEmotionVO getSessionEmotion(String sessionId);

}
