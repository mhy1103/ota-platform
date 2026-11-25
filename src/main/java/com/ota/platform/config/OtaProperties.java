package com.ota.platform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ota")
public class OtaProperties {

    private String dataDir = "./data";
    private String publicUrl = "http://localhost:8812";
    private String displayZone = "Asia/Shanghai";
    private String masterKey = "";
    private String bootstrapToken = "";
    private String adminPassword = "";
    private long maxAssetBytes = 200L * 1024 * 1024;
    /** 兑换后不到这么多毫秒就来确认的判为刷的（真手机要先下载 B 面包、再重启切过去），0 不判 */
    private long minConfirmMs = 2000;
    private Storage storage = new Storage();
    private Patch patch = new Patch();

    public static class Storage {
        private String type = "local";
        private S3 s3 = new S3();

        public boolean isS3() { return "s3".equalsIgnoreCase(type); }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public S3 getS3() { return s3; }
        public void setS3(S3 s3) { this.s3 = s3; }
    }

    public static class S3 {
        private String endpoint = "";
        private String region = "us-east-1";
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        private boolean pathStyle = false;
        private String prefix = "ota/";
        private String publicUrl = "";

        public String getEndpoint() { return endpoint; }
        public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
        public String getRegion() { return region; }
        public void setRegion(String region) { this.region = region; }
        public String getBucket() { return bucket; }
        public void setBucket(String bucket) { this.bucket = bucket; }
        public String getAccessKey() { return accessKey; }
        public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
        public String getSecretKey() { return secretKey; }
        public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
        public boolean isPathStyle() { return pathStyle; }
        public void setPathStyle(boolean pathStyle) { this.pathStyle = pathStyle; }
        public String getPrefix() { return prefix == null ? "" : prefix; }
        public void setPrefix(String prefix) { this.prefix = prefix; }
        public String getPublicUrl() { return publicUrl; }
        public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    }

    public static class Patch {
        private boolean enabled = true;
        private int bases = 3;
        private double maxRatio = 0.8;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getBases() { return bases; }
        public void setBases(int bases) { this.bases = bases; }
        public double getMaxRatio() { return maxRatio; }
        public void setMaxRatio(double maxRatio) { this.maxRatio = maxRatio; }
    }

    /** 去掉末尾斜杠的对外地址 */
    public String baseUrl() {
        return publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    public String getDataDir() { return dataDir; }
    public void setDataDir(String dataDir) { this.dataDir = dataDir; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getDisplayZone() { return displayZone; }
    public void setDisplayZone(String displayZone) { this.displayZone = displayZone; }
    public String getMasterKey() { return masterKey; }
    public void setMasterKey(String masterKey) { this.masterKey = masterKey; }
    public String getBootstrapToken() { return bootstrapToken; }
    public void setBootstrapToken(String bootstrapToken) { this.bootstrapToken = bootstrapToken; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String adminPassword) { this.adminPassword = adminPassword; }
    public long getMaxAssetBytes() { return maxAssetBytes; }
    public void setMaxAssetBytes(long maxAssetBytes) { this.maxAssetBytes = maxAssetBytes; }
    public long getMinConfirmMs() { return minConfirmMs; }
    public void setMinConfirmMs(long minConfirmMs) { this.minConfirmMs = minConfirmMs; }
    public Storage getStorage() { return storage; }
    public void setStorage(Storage storage) { this.storage = storage; }
    public Patch getPatch() { return patch; }
    public void setPatch(Patch patch) { this.patch = patch; }
}
