package com.ruwei.manager;

import cn.hutool.core.util.StrUtil;
import com.ruwei.config.MinioClientConfig;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 通用的文件处理类（基于 MinIO，存储逻辑与原 COS 版本一致：
 * 上传原始图 + 生成 webp 展示图；GIF 动图不做 webp 转换，保留原图）。
 * 注：MinIO 没有服务端图片处理（原 COS 数据万象 PicOperations），
 * webp 转换改为本地转换后分别上传原图和 webp 展示图。
 */
@Component
@ConditionalOnProperty(prefix = "storage", name = "type", havingValue = "minio")
public class MinioManager implements ObjectStorageManager {

    @Resource
    private MinioClientConfig minioClientConfig;

    @Resource
    private MinioClient minioClient;

    /**
     * 上传对象
     *
     * @param key  唯一键（是文件夹加上文件的名字）
     * @param file 文件
     */
    @Override
    public UploadResult putObject(String key, File file) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(minioClientConfig.getBucketName())
                .object(key)
                .stream(new FileInputStream(file), file.length(), -1)
                .build());
        return buildMinimalResult(key, file.length());
    }

    /**
     * 下载对象
     *
     * @param key 唯一键（是文件夹加上文件的名字）
     */
    @Override
    public InputStream getObject(String key) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioClientConfig.getBucketName())
                .object(key)
                .build());
    }

    /**
     * 上传图片：上传原始图并生成 webp 展示图，返回上传结果。
     * GIF 动图不做 webp 转换，保留原图。
     *
     * @param key         MinIO 对象 key（服务端生成，不允许使用前端文件名）
     * @param bytes       图片字节
     * @param contentType 图片 MIME 类型
     */
    @Override
    public UploadResult uploadImage(String key, byte[] bytes, String contentType) throws Exception {
        String webpKey = null;
        byte[] webpBytes = null;
        if (!"image/gif".equalsIgnoreCase(contentType)) {
            webpKey = toWebpKey(key);
            webpBytes = convertToWebp(bytes);
        }

        // 先传原图；转换成功则再传 webp 展示图
        putObject(key, bytes, contentType);
        if (webpBytes != null) {
            putObject(webpKey, webpBytes, "image/webp");
        }

        return buildUploadResult(key, webpKey, bytes, webpBytes, contentType);
    }

    /**
     * 上传对象（附带图片处理：生成 webp 展示图）
     *
     * @param key  唯一键
     * @param file 文件
     */
    @Override
    public UploadResult putPictureObject(String key, File file) throws Exception {
        byte[] bytes;
        try (InputStream in = new FileInputStream(file)) {
            bytes = in.readAllBytes();
        }
        String webpKey = toWebpKey(key);
        byte[] webpBytes = convertToWebp(bytes);
        putObject(key, bytes, null);
        if (webpBytes != null) {
            putObject(webpKey, webpBytes, "image/webp");
        }
        String displayKey = webpBytes != null ? webpKey : key;
        return new UploadResult(key, displayKey, buildUrl(key), buildUrl(displayKey), null, null,
                (long) bytes.length, null);
    }

    /**
     * 删除对象
     *
     * @param key 文件 key
     */
    @Override
    public void deleteObject(String key) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(minioClientConfig.getBucketName())
                .object(key)
                .build());
    }

    private void putObject(String key, byte[] bytes, String contentType) throws Exception {
        PutObjectArgs.Builder builder = PutObjectArgs.builder()
                .bucket(minioClientConfig.getBucketName())
                .object(key)
                .stream(new ByteArrayInputStream(bytes), bytes.length, -1);
        if (contentType != null) {
            builder.contentType(contentType);
        }
        minioClient.putObject(builder.build());
    }

    private UploadResult buildUploadResult(String key, String webpKey, byte[] bytes,
                                           byte[] webpBytes, String contentType) {
        String displayKey = webpKey != null && webpBytes != null ? webpKey : key;
        String originalUrl = buildUrl(key);
        String displayUrl = buildUrl(displayKey);
        Integer width = null;
        Integer height = null;

        // 优先读 webp 展示图尺寸，读不到再读原图
        ImageSize size = webpBytes != null ? readImageSize(webpBytes) : null;
        if (size == null) {
            size = readImageSize(bytes);
        }
        if (size != null) {
            width = size.width;
            height = size.height;
        }

        return new UploadResult(key, displayKey, originalUrl, displayUrl, width, height,
                (long) bytes.length, contentType);
    }

    private UploadResult buildMinimalResult(String key, long size) {
        return new UploadResult(key, key, buildUrl(key), buildUrl(key), null, null, size, null);
    }

    private String buildUrl(String key) {
        String endpoint = StrUtil.removeSuffix(minioClientConfig.getEndpoint(), "/");
        String bucket = minioClientConfig.getBucketName();
        return endpoint + "/" + bucket + "/" + key;
    }

    private String toWebpKey(String key) {
        int dot = key.lastIndexOf('.');
        return dot < 0 ? key + ".webp" : key.substring(0, dot) + ".webp";
    }

    /**
     * 本地把图片字节转换为 webp（MinIO 无服务端图片处理，用 ImageIO 的 webp 插件转换）。
     * 返回 null 表示转换失败，调用方会回退为只展示原图。
     */
    private byte[] convertToWebp(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                return null;
            }
            if (!ImageIO.write(image, "webp", out)) {
                return null;
            }
            return out.toByteArray();
        } catch (IOException e) {
            return null;
        }
    }

    private ImageSize readImageSize(byte[] bytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            BufferedImage image = ImageIO.read(in);
            return image == null ? null : new ImageSize(image.getWidth(), image.getHeight());
        } catch (IOException e) {
            return null;
        }
    }

    private record ImageSize(int width, int height) {
    }
}
