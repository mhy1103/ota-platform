package com.ota.platform.install;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;

/**
 * 安装引导填的配置，存在数据目录的 install.json。启动时转成 Spring 配置项（启动参数、环境变量仍然优先）。
 * 密码、密钥这些字段存的是用主密钥加密后的密文。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class InstallConfig {

    public boolean installed;
    public String installedAt;
    public String version;
    /** 从旧版本升级时自动生成的（不是通过安装引导） */
    public boolean migrated;

    public Database db = new Database();
    /** 下次启动重建所有表（安装时选了清空数据），启动时用一次就清掉 */
    public boolean resetSchema;
    /** 安装后第一次启动时要建的超级管理员（密码是 BCrypt 哈希），用完删掉 */
    public PendingAdmin pendingAdmin;

    /** 只允许下面这些域名访问；false 表示任何域名、IP 都能访问 */
    public boolean restrictDomains;
    public List<Domain> domains = new ArrayList<>();
    /** App 下载文件用的地址（清单里的 URL 前缀），一般是第一个接口域名 */
    public String publicUrl;
    public String displayZone;

    public Storage storage = new Storage();

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Database {
        /** h2：内置数据库；mysql */
        public String type = "h2";
        public String host;
        public Integer port;
        public String name;
        public String username;
        public String password;

        public boolean mysql() { return "mysql".equals(type); }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PendingAdmin {
        public String username;
        public String name;
        public String passwordHash;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Domain {
        /** 域名或 IP，不带协议，可以带端口 */
        public String host;
        public boolean admin;
        public boolean api;
        public boolean https = true;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Storage {
        /** local：本机；s3：S3 兼容对象存储 */
        public String type = "local";
        public String endpoint;
        public String region;
        public String bucket;
        public String accessKey;
        public String secretKey;
        public boolean pathStyle;
        public String prefix;
        public String publicUrl;
    }
}
