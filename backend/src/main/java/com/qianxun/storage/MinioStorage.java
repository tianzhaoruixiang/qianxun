package com.qianxun.storage;

import com.qianxun.config.QianxunProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.Locale;

/**
 * 用户文档对象存储。对象键：{@code users/{userId}/{fileId}/{filename}}。
 */
@Component
public class MinioStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioStorage.class);

    private final QianxunProperties properties;
    private volatile MinioClient client;
    private volatile boolean bucketReady;

    public MinioStorage(QianxunProperties properties) {
        this.properties = properties;
    }

    public boolean isEnabled() {
        QianxunProperties.Minio m = properties.getMinio();
        return m != null && !blank(m.getEndpoint()) && !blank(m.getAccessKey()) && !blank(m.getBucket());
    }

    public String putUserFile(
            String userId,
            String fileId,
            String filename,
            InputStream in,
            long size,
            String contentType
    ) {
        ensureReady();
        String key = objectKey(userId, fileId, filename);
        String type = blank(contentType) ? "application/octet-stream" : contentType.trim();
        try {
            client().putObject(PutObjectArgs.builder()
                    .bucket(bucket())
                    .object(key)
                    .stream(in, size, -1)
                    .contentType(type)
                    .build());
            return key;
        } catch (Exception ex) {
            throw new IllegalStateException("上传到对象存储失败: " + ex.getMessage(), ex);
        }
    }

    public InputStream getObject(String objectKey) {
        if (blank(objectKey)) {
            throw new IllegalArgumentException("objectKey 不能为空");
        }
        ensureReady();
        try {
            return client().getObject(GetObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            throw new IllegalStateException("读取对象失败: " + ex.getMessage(), ex);
        }
    }

    public void deleteObject(String objectKey) {
        if (blank(objectKey)) {
            return;
        }
        try {
            ensureReady();
            client().removeObject(RemoveObjectArgs.builder()
                    .bucket(bucket())
                    .object(objectKey)
                    .build());
        } catch (Exception ex) {
            log.warn("删除对象失败 {}: {}", objectKey, ex.toString());
        }
    }

    public static String objectKey(String userId, String fileId, String filename) {
        return "users/" + safeSegment(userId, "1") + "/" + safeSegment(fileId, "file")
                + "/" + safeFilename(filename);
    }

    private void ensureReady() {
        if (!isEnabled()) {
            throw new IllegalStateException("未配置 MinIO（qianxun.minio.endpoint / access-key / bucket）");
        }
        client();
        if (bucketReady) {
            return;
        }
        synchronized (this) {
            if (bucketReady) {
                return;
            }
            String bucket = bucket();
            try {
                boolean exists = client().bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
                if (!exists) {
                    client().makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                    log.info("已创建 MinIO bucket: {}", bucket);
                }
                bucketReady = true;
            } catch (Exception ex) {
                throw new IllegalStateException("MinIO bucket 不可用: " + ex.getMessage(), ex);
            }
        }
    }

    private MinioClient client() {
        MinioClient c = this.client;
        if (c != null) {
            return c;
        }
        synchronized (this) {
            if (this.client != null) {
                return this.client;
            }
            QianxunProperties.Minio m = properties.getMinio();
            this.client = MinioClient.builder()
                    .endpoint(m.getEndpoint().trim())
                    .credentials(m.getAccessKey().trim(), nullToEmpty(m.getSecretKey()))
                    .build();
            return this.client;
        }
    }

    private String bucket() {
        return properties.getMinio().getBucket().trim();
    }

    static String safeSegment(String raw, String fallback) {
        String s = raw == null ? "" : raw.trim();
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch) || ch == '-' || ch == '_' || ch == '.') {
                out.append(ch);
            } else {
                out.append('_');
            }
        }
        String v = out.toString().replace("..", "_");
        return v.isBlank() ? fallback : v;
    }

    static String safeFilename(String filename) {
        String name = filename == null ? "" : filename.trim();
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = safeSegment(name, "file");
        if (name.length() > 180) {
            String ext = "";
            int dot = name.lastIndexOf('.');
            if (dot > 0 && dot < name.length() - 1) {
                ext = name.substring(dot);
                name = name.substring(0, dot);
            }
            name = name.substring(0, Math.max(1, 180 - ext.length())) + ext;
        }
        return name.toLowerCase(Locale.ROOT).equals(".") ? "file" : name;
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    private static String nullToEmpty(String v) {
        return v == null ? "" : v;
    }
}
