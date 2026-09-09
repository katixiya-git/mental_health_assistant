package com.ai.aiproject.dto.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新增/更新知识文章命令 DTO
 */
@Data
public class KnowledgeArticleSaveCommandDTO {

    @NotNull(message = "分类不能为空")
    private Long categoryId;

    @NotBlank(message = "标题不能为空")
    @Size(max = 200, message = "标题长度不能超过200个字符")
    private String title;

    @Size(max = 1000, message = "摘要长度不能超过1000个字符")
    private String summary;

    @NotBlank(message = "正文不能为空")
    @Size(max = 100000, message = "正文长度超限")
    private String content;

    @Size(max = 255, message = "封面路径长度不能超过255个字符")
    private String coverImage;

    @Size(max = 500, message = "标签长度不能超过500个字符")
    private String tags;
}
