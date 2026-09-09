package com.ai.aiproject.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件存储服务
 */
public interface FileStorageService {

    /**
     * 上传图片到 OSS
     *
     * @param file 图片文件（≤5MB）
     * @return 相对路径（以 / 开头）
     */
    String uploadImage(MultipartFile file);
}
