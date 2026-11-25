package com.ota.platform.install;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 启动时把 install.json 转成配置项：数据库、对外地址、文件存储、时区。
 * 优先级低于启动参数和环境变量，高于 application.yml，所以老的启动方式仍然有效。
 */
public class InstallPropertySource implements EnvironmentPostProcessor, Ordered {

    static final String NAME = "otaInstall";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        String dataDir = env.getProperty("ota.data-dir", "./data");
        InstallConfig c = InstallFiles.read(dataDir);
        if (c == null) return;
        String mk = env.getProperty("ota.master-key", "");
        Map<String, Object> p = new LinkedHashMap<>();

        if (c.db != null && c.db.mysql() && notBlank(c.db.host)) {
            p.put("spring.datasource.url", mysqlUrl(c.db));
            p.put("spring.datasource.username", nz(c.db.username));
            p.put("spring.datasource.password", nz(InstallFiles.decrypt(dataDir, mk, c.db.password)));
        }
        if (c.resetSchema) {
            p.put("spring.jpa.hibernate.ddl-auto", "create");
            c.resetSchema = false;
            InstallFiles.write(dataDir, c);
        }
        if (notBlank(c.publicUrl)) p.put("ota.public-url", c.publicUrl.trim());
        if (notBlank(c.displayZone)) p.put("ota.display-zone", c.displayZone.trim());

        InstallConfig.Storage s = c.storage;
        if (s != null && "s3".equals(s.type)) {
            p.put("ota.storage.type", "s3");
            p.put("ota.storage.s3.endpoint", nz(s.endpoint));
            p.put("ota.storage.s3.region", notBlank(s.region) ? s.region : "us-east-1");
            p.put("ota.storage.s3.bucket", nz(s.bucket));
            p.put("ota.storage.s3.access-key", nz(s.accessKey));
            p.put("ota.storage.s3.secret-key", nz(InstallFiles.decrypt(dataDir, mk, s.secretKey)));
            p.put("ota.storage.s3.path-style", String.valueOf(s.pathStyle));
            if (s.prefix != null) p.put("ota.storage.s3.prefix", s.prefix);
            p.put("ota.storage.s3.public-url", nz(s.publicUrl));
        }
        if (p.isEmpty()) return;
        MapPropertySource source = new MapPropertySource(NAME, p);
        if (env.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            env.getPropertySources().addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
        } else {
            env.getPropertySources().addLast(source);
        }
    }

    static String mysqlUrl(InstallConfig.Database db) {
        int port = db.port == null || db.port <= 0 ? 3306 : db.port;
        return "jdbc:mysql://" + db.host.trim() + ":" + port + "/" + db.name.trim()
                + "?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8";
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /** 在读取 application.yml 之后执行，才能拿到 ota.data-dir */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
