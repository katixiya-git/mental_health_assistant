package com.ai.aiproject.Utils;

import com.ai.aiproject.Exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OssFileToolTest {

    @Test
    void extractExtension() {
        assertEquals("png", OssFileTool.extractExt("a.PNG"));
        assertEquals("webp", OssFileTool.extractExt("x.webp"));
        assertEquals("", OssFileTool.extractExt("noext"));
        assertEquals("jpg", OssFileTool.extractExt("a.b.jpg"));
    }

    @Test
    void allowedImageCheck() {
        assertTrue(OssFileTool.isAllowedImage("c.png"));
        assertTrue(OssFileTool.isAllowedImage("c.jpeg"));
        assertTrue(OssFileTool.isAllowedImage("c.webp"));
        assertFalse(OssFileTool.isAllowedImage("c.txt"));
        assertFalse(OssFileTool.isAllowedImage("c.exe"));
    }

    @Test
    void validateImageFilePassAndFail() {
        assertDoesNotThrow(() -> OssFileTool.validateImageFile("ok.jpg", 1024));
        assertThrows(BusinessException.class, () -> OssFileTool.validateImageFile("bad.gif", 6L * 1024 * 1024)); // 超 5MB
        assertThrows(BusinessException.class, () -> OssFileTool.validateImageFile("bad.js", 1024));
    }

    @Test
    void buildObjectKeyMatchesPattern() {
        String key = OssFileTool.buildObjectKey("myPhoto.PNG");
        assertTrue(key.matches("article/cover/\\d{6}/[0-9a-f-]{36}\\.png"), key);
        assertFalse(key.startsWith("/"));
    }
}
