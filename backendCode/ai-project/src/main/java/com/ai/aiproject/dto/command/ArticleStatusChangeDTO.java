package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文章状态变更 DTO（1发布 / 2下线）
 */
@Data
public class ArticleStatusChangeDTO {

    @NotNull(message = "状态不能为空")
    @Min(value = 1, message = "状态仅支持 1发布/2下线")
    @Max(value = 2, message = "状态仅支持 1发布/2下线")
    private Integer status;
}
