package com.ai.aiproject.controller;

import com.ai.aiproject.common.Result;
import com.ai.aiproject.dto.response.FileUploadResponseDTO;
import com.ai.aiproject.service.FileStorageService;
import jakarta.annotation.Resource;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件控制器
 * <p>
 * POST /api/file/upload（需登录；业务字段 businessType/businessId/businessField 本期仅接收不落库）
 */
@RestController
@RequestMapping("/api/file")
public class FileController {

    @Resource
    private FileStorageService fileStorageService;

    @PostMapping("/upload")
    public Result<FileUploadResponseDTO> upload(@RequestParam("file") MultipartFile file) {
        return Result.ok(new FileUploadResponseDTO(fileStorageService.uploadImage(file)));
    }
}
