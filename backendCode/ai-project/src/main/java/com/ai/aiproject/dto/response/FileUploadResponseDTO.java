package com.ai.aiproject.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件上传响应 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FileUploadResponseDTO {

    /** 相对路径（如 /article/cover/202609/uuid.jpg），前端拼 OSS url-prefix 显示 */
    private String filePath;
}
