package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import com.ai.aiproject.enums.ResultCode;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * OSS 文件纯静态工具：扩展名校验/图片类型与大小校验/对象键生成（便于无 Spring/Mockito 单测）
 */
public final class OssFileTool {

    /** 允许的图片扩展名 */
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    /** 上传大小上限 5MB */
    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;

    private OssFileTool() {
    }

    /** 取小写扩展名（含点后部分），无扩展名返回 "" */
    public static String extractExt(String filename) {
        if (filename == null) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    public static boolean isAllowedImage(String filename) {
        return ALLOWED_EXT.contains(extractExt(filename));
    }

    /**
     * 校验：类型必须为图片且 ≤5MB
     *
     * @throws BusinessException FILE_TYPE_NOT_SUPPORTED / FILE_SIZE_EXCEEDED
     */
    public static void validateImageFile(String originalFilename, long sizeBytes) {
        if (!isAllowedImage(originalFilename)) {
            throw new BusinessException(ResultCode.FILE_TYPE_NOT_SUPPORTED.getCode(), ResultCode.FILE_TYPE_NOT_SUPPORTED.getMsg());
        }
        if (sizeBytes > MAX_IMAGE_SIZE) {
            throw new BusinessException(ResultCode.FILE_SIZE_EXCEEDED.getCode(), ResultCode.FILE_SIZE_EXCEEDED.getMsg());
        }
    }

    /**
     * 生成对象键（不带前导斜杠）：article/cover/yyyyMM/uuid.ext
     */
    public static String buildObjectKey(String originalFilename) {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMM"));
        String ext = extractExt(originalFilename);
        return "article/cover/" + date + "/" + UUID.randomUUID() + "." + ext;
    }
}
