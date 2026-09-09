package com.ai.aiproject.service.Impl;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.Utils.OssFileTool;
import com.ai.aiproject.config.OssProperties;
import com.ai.aiproject.enums.ResultCode;
import com.ai.aiproject.service.FileStorageService;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * 文件存储服务实现（阿里云 OSS，公共读）
 */
@Service
@RequiredArgsConstructor
public class FileStorageServiceImpl implements FileStorageService {

    private final OssProperties ossProperties;

    @Override
    public String uploadImage(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        OssFileTool.validateImageFile(originalName, file.getSize());
        String objectKey = OssFileTool.buildObjectKey(originalName);

        OSS ossClient = new OSSClientBuilder()
                .build(ossProperties.getEndpoint(), ossProperties.getAccessKeyId(), ossProperties.getAccessKeySecret());
        try (InputStream in = file.getInputStream()) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(file.getSize());
            ossClient.putObject(ossProperties.getBucket(), objectKey, in, meta);
            return "/" + objectKey;
        } catch (IOException e) {
            throw new BusinessException(ResultCode.FILE_UPLOAD_FAILED.getCode(), ResultCode.FILE_UPLOAD_FAILED.getMsg());
        } finally {
            ossClient.shutdown();
        }
    }
}
