package com.ai.aiproject.dto.query;

import lombok.Data;

/**
 * 会话分页查询 DTO：兼容用户端 pageNum/pageSize 与管理端 currentPage/size
 */
@Data
public class SessionPageQueryDTO {

    private int currentPage = 1;

    private int size = 10;

    private int pageNum;

    private int pageSize;
}
