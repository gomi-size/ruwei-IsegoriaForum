package com.ruwei.manager;

/**
 * 统一的对象/图片上传结果：COS 与 MinIO 共用。
 * 展示图（displayKey/displayUrl）默认为 webp 压缩图；GIF 动图不转换，与原图一致。
 */
public record UploadResult(String originalKey, String displayKey, String originalUrl,
                           String displayUrl, Integer width, Integer height,
                           Long size, String contentType) {
}
