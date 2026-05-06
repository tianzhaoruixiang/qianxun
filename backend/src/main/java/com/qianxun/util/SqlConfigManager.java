package com.qianxun.util;

import com.qianxun.constant.CacheConstant;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * 加载数据库/ES 连接信息。
 * 历史版本依赖私有解密库 ShareAES，这里改为：
 * - 优先读取环境变量（便于容器部署）
 * - 其次读取配置文件中的明文字段
 * 保持原有 getter API 不变，避免影响其它调用方。
 */
@Getter
@Slf4j
public final class SqlConfigManager {
    private static SqlConfigManager svcManager = null;
    private String metaDbUser;
    private String metaDbPassword;
    private String metaTdUser;
    private String metaTdPassword;
    private String esUser;
    private String esPassword;

    private SqlConfigManager() {
        try(InputStream in = new BufferedInputStream(new FileInputStream(CacheConstant.getFilePath()))){
            Properties properties = new Properties();
            properties.load(in);
            metaTdUser = pick("META_TD_USER", properties, "meta.td.user", "mtdu");
            metaTdPassword = pick("META_TD_PASSWORD", properties, "meta.td.password", "mtdp");
            metaDbUser = pick("META_DB_USER", properties, "meta.db.user", "mdbu");
            metaDbPassword = pick("META_DB_PASSWORD", properties, "meta.db.password", "mdbp");
            esUser = pick("ES_USER", properties, "es.user", "besu");
            esPassword = pick("ES_PASSWORD", properties, "es.password", "besp");
        }catch (Exception e){
            log.error("get the user and password failed: ", e);
        }
    }

    private static String pick(String envKey, Properties properties, String... keys) {
        String fromEnv = System.getenv(envKey);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.trim();
        }
        for (String key : keys) {
            String value = properties.getProperty(key);
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    public static SqlConfigManager getInstance() {
        if (svcManager == null) {
            svcManager = new SqlConfigManager();
        }
        return svcManager;
    }
}
