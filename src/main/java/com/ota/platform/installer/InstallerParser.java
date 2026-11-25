package com.ota.platform.installer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * 从安装包里读 expo-updates 的配置。
 * APK：AndroidManifest.xml（二进制 XML）的 meta-data，@string 引用查 resources.arsc，再看 assets/app.config、assets/app.manifest；
 * IPA：Info.plist、Expo.plist，再看 EXConstants.bundle/app.config、EXUpdates.bundle/app.manifest。
 * 安装包里的配置优先，读不到再用 app.config 里的。
 */
final class InstallerParser {

    record Info(String platform, String bundleId, String versionName, String buildNumber, String runtimeVersion, String updateUrl,
                String channel, String certificatePem, String keyId, String embeddedUpdateId, String sdkVersion, Boolean updatesEnabled,
                String extraJson, List<String> warnings) {}

    /** 读法的版本，记在 extra 里。读法改了就加一，旧记录检查时会从存着的安装包重新读 */
    static final int VERSION = 2;

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final long MAX_ENTRY = 32L << 20;
    private static final Pattern IOS_APP = Pattern.compile("^(Payload/[^/]+\\.app/)Info\\.plist$");
    private static final String META = "expo.modules.updates.";

    private InstallerParser() {
    }

    static Info parse(Path file) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            if (zip.getEntry("AndroidManifest.xml") != null) return android(zip);
            for (Enumeration<? extends ZipEntry> e = zip.entries(); e.hasMoreElements(); ) {
                Matcher m = IOS_APP.matcher(e.nextElement().getName());
                if (m.matches()) return ios(zip, m.group(1));
            }
        } catch (ZipException e) {
            throw new IOException("不是有效的安装包：APK 和 IPA 都是 zip 格式的文件");
        }
        throw new IOException("没找到 AndroidManifest.xml 或 Payload/*.app，不是 APK / IPA 安装包");
    }

    // ---------- Android ----------

    private static Info android(ZipFile zip) throws IOException {
        List<String> warn = new ArrayList<>();
        Axml.Manifest mf = Axml.parse(read(zip, "AndroidManifest.xml"));
        Arsc res = null;
        if (zip.getEntry("resources.arsc") != null) {
            try {
                res = Arsc.parse(read(zip, "resources.arsc"));
            } catch (IOException | RuntimeException e) {
                warn.add("resources.arsc 读不懂，引用的字符串取不到");
            }
        }
        JsonNode cfg = json(zip, "assets/app.config", warn);
        JsonNode emb = json(zip, "assets/app.manifest", warn);
        Map<String, Axml.Value> meta = mf.meta();
        if (meta.keySet().stream().noneMatch(k -> k.startsWith(META))) warn.add("AndroidManifest.xml 里没有 expo-updates 的配置，这个安装包可能没装 expo-updates");

        String runtime = orRuntime(text(meta.get(META + "EXPO_RUNTIME_VERSION"), res, warn, "运行版本"), cfg, warn);
        String url = first(text(meta.get(META + "EXPO_UPDATE_URL"), res, warn, "更新地址"), path(cfg, "updates", "url"));
        String channel = channel(jsonText(text(meta.get(META + "UPDATES_CONFIGURATION_REQUEST_HEADERS_KEY"), res, warn, "请求头")), cfg);
        String cert = text(meta.get(META + "CODE_SIGNING_CERTIFICATE"), res, warn, "签名证书");
        JsonNode certMeta = jsonText(text(meta.get(META + "CODE_SIGNING_METADATA"), res, warn, "签名信息"));
        String enabledText = text(meta.get(META + "ENABLED"), res, warn, "更新开关");
        Boolean enabled = enabledText == null ? bool(cfg, "updates", "enabled") : Boolean.valueOf("true".equalsIgnoreCase(enabledText.trim()));
        ObjectNode extra = extras(
                first(text(meta.get(META + "EXPO_UPDATES_CHECK_ON_LAUNCH"), res, warn, "检查时机"), path(cfg, "updates", "checkAutomatically")),
                first(text(meta.get(META + "EXPO_UPDATES_LAUNCH_WAIT_MS"), res, warn, "启动等待"), path(cfg, "updates", "fallbackToCacheTimeout")),
                emb, certMeta, zip.getEntry("assets/app.manifest") != null,
                cleartext(mf.usesCleartextTraffic(), res, warn));
        return new Info("android", mf.packageName(), text(mf.versionName(), res, warn, "版本名"), number(mf.versionCode()),
                runtime, url, channel, blankToNull(cert), certMeta == null ? null : textOf(certMeta.get("keyid")),
                emb == null ? null : textOf(emb.get("id")), path(cfg, "sdkVersion"), enabled, extra.toString(), warn);
    }

    private static String text(Axml.Value v, Arsc res, List<String> warn, String what) {
        if (v == null) return null;
        if (v.string() != null) return v.string();
        return switch (v.type()) {
            case Axml.TYPE_REFERENCE -> {
                String s = res == null ? null : res.resolve(v.data());
                if (s == null) warn.add(what + "引用了资源 0x" + Integer.toHexString(v.data()) + "，没取到值");
                yield s;
            }
            case Axml.TYPE_BOOLEAN -> v.data() != 0 ? "true" : "false";
            case Axml.TYPE_INT_DEC, Axml.TYPE_INT_HEX -> String.valueOf(v.data());
            default -> null;
        };
    }

    private static String number(Axml.Value v) {
        if (v == null) return null;
        if (v.string() != null) return v.string();
        return v.type() == Axml.TYPE_INT_DEC || v.type() == Axml.TYPE_INT_HEX ? String.valueOf(v.data()) : null;
    }

    // ---------- iOS ----------

    private static Info ios(ZipFile zip, String dir) throws IOException {
        List<String> warn = new ArrayList<>();
        Map<String, Object> info = Plist.dict(read(zip, dir + "Info.plist"));
        Map<String, Object> expo = null;
        if (zip.getEntry(dir + "Expo.plist") != null) expo = Plist.dict(read(zip, dir + "Expo.plist"));
        else warn.add("没有 Expo.plist，这个安装包可能没装 expo-updates");
        JsonNode cfg = json(zip, dir + "EXConstants.bundle/app.config", warn);
        JsonNode emb = json(zip, dir + "EXUpdates.bundle/app.manifest", warn);
        Map<String, Object> e = expo == null ? Map.of() : expo;
        String channel = null;
        if (e.get("EXUpdatesRequestHeaders") instanceof Map<?, ?> h && h.get("expo-channel-name") != null) channel = String.valueOf(h.get("expo-channel-name"));
        if (channel == null) channel = channel(null, cfg);
        String keyId = null;
        if (e.get("EXUpdatesCodeSigningMetadata") instanceof Map<?, ?> m && m.get("keyid") != null) keyId = String.valueOf(m.get("keyid"));
        Object enabled = e.get("EXUpdatesEnabled");
        JsonNode certMeta = e.get("EXUpdatesCodeSigningMetadata") instanceof Map<?, ?> m
                ? JSON.valueToTree(m) : jsonText(str(e.get("EXUpdatesCodeSigningMetadata")));
        ObjectNode extra = extras(
                first(str(e.get("EXUpdatesCheckOnLaunch")), path(cfg, "updates", "checkAutomatically")),
                first(str(e.get("EXUpdatesLaunchWaitMs")), path(cfg, "updates", "fallbackToCacheTimeout")),
                emb, certMeta, zip.getEntry(dir + "EXUpdates.bundle/app.manifest") != null, null);
        ats(extra, info);
        return new Info("ios", str(info.get("CFBundleIdentifier")), str(info.get("CFBundleShortVersionString")), str(info.get("CFBundleVersion")),
                orRuntime(str(e.get("EXUpdatesRuntimeVersion")), cfg, warn), first(str(e.get("EXUpdatesURL")), path(cfg, "updates", "url")),
                channel, blankToNull(str(e.get("EXUpdatesCodeSigningCertificate"))), keyId, emb == null ? null : textOf(emb.get("id")),
                path(cfg, "sdkVersion"), enabled instanceof Boolean b ? b : bool(cfg, "updates", "enabled"), extra.toString(), warn);
    }

    // ---------- 通用 ----------

    private static byte[] read(ZipFile zip, String name) throws IOException {
        ZipEntry e = zip.getEntry(name);
        if (e == null) throw new IOException("安装包里没有 " + name);
        if (e.getSize() > MAX_ENTRY) throw new IOException(name + " 太大");
        try (InputStream in = zip.getInputStream(e)) {
            byte[] out = in.readNBytes((int) MAX_ENTRY + 1);
            if (out.length > MAX_ENTRY) throw new IOException(name + " 太大");
            return out;
        }
    }

    private static JsonNode json(ZipFile zip, String name, List<String> warn) {
        if (zip.getEntry(name) == null) return null;
        try {
            return JSON.readTree(read(zip, name));
        } catch (IOException e) {
            warn.add(name + " 读不懂");
            return null;
        }
    }

    private static JsonNode jsonText(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return JSON.readTree(s);
        } catch (IOException e) {
            return null;
        }
    }

    /** 安装包原生配置里的运行版本优先；没有时看 app.config（策略能算出来的算出来） */
    private static String orRuntime(String nativeValue, JsonNode cfg, List<String> warn) {
        if (nativeValue != null && !nativeValue.isBlank()) return nativeValue;
        if (cfg == null || cfg.get("runtimeVersion") == null) return null;
        JsonNode rv = cfg.get("runtimeVersion");
        if (rv.isTextual()) return rv.asText();
        String policy = rv.path("policy").asText("");
        if (policy.equals("appVersion") && cfg.hasNonNull("version")) return cfg.get("version").asText();
        if (policy.equals("sdkVersion") && cfg.hasNonNull("sdkVersion")) return "exposdk:" + cfg.get("sdkVersion").asText();
        if (!policy.isEmpty()) warn.add("运行版本是按策略（" + policy + "）在打包时算出来的，安装包里没找到具体的值");
        return null;
    }

    private static String channel(JsonNode headers, JsonNode cfg) {
        if (headers != null && headers.get("expo-channel-name") != null) return headers.get("expo-channel-name").asText();
        JsonNode h = cfg == null ? null : cfg.path("updates").get("requestHeaders");
        return h != null && h.get("expo-channel-name") != null ? h.get("expo-channel-name").asText() : null;
    }

    private static String path(JsonNode n, String... keys) {
        JsonNode cur = n;
        for (String k : keys) {
            if (cur == null) return null;
            cur = cur.get(k);
        }
        return textOf(cur);
    }

    private static Boolean bool(JsonNode n, String... keys) {
        String s = path(n, keys);
        return s == null ? null : Boolean.valueOf(s);
    }

    private static String textOf(JsonNode n) {
        return n == null || n.isNull() || n.isContainerNode() ? null : n.asText();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static String first(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ObjectNode extras(String checkOnLaunch, String launchWait, JsonNode emb, JsonNode certMeta,
                                     boolean hasEmbedded, Boolean cleartext) {
        ObjectNode extra = JSON.createObjectNode();
        extra.put("parser", VERSION);
        if (checkOnLaunch != null && !checkOnLaunch.isBlank()) extra.put("checkOnLaunch", checkOnLaunch.trim());
        if (launchWait != null && !launchWait.isBlank()) extra.put("launchWaitMs", launchWait.trim());
        if (emb != null) {
            // 内置清单里时间是 commitTime（毫秒）或 createdAt（ISO），都可能出现
            JsonNode ct = emb.get("commitTime");
            if (ct != null && ct.isNumber()) extra.put("embeddedCommitMillis", ct.asLong());
            else if (ct != null && ct.isTextual() && !ct.asText().isBlank()) extra.put("embeddedCreatedAt", ct.asText());
            else if (emb.get("createdAt") != null) extra.put("embeddedCreatedAt", textOf(emb.get("createdAt")));
        }
        extra.put("hasEmbeddedManifest", hasEmbedded);
        if (cleartext != null) extra.put("cleartextAllowed", cleartext);
        if (certMeta != null && certMeta.get("alg") != null) extra.put("signingAlg", textOf(certMeta.get("alg")));
        return extra;
    }

    private static Boolean cleartext(Axml.Value v, Arsc res, List<String> warn) {
        String s = text(v, res, warn, "明文流量");
        if (s == null) return null;
        return "true".equalsIgnoreCase(s.trim()) || "1".equals(s.trim());
    }

    private static void ats(ObjectNode extra, Map<String, Object> info) {
        if (info.get("NSAppTransportSecurity") instanceof Map<?, ?> m) extra.put("cleartextAllowed", atsAllowsInsecure(m));
    }

    private static boolean atsAllowsInsecure(Map<?, ?> ats) {
        if (Boolean.TRUE.equals(ats.get("NSAllowsArbitraryLoads"))) return true;
        Object domains = ats.get("NSExceptionDomains");
        if (!(domains instanceof Map<?, ?> d)) return false;
        for (Object v : d.values()) {
            if (v instanceof Map<?, ?> e && (Boolean.TRUE.equals(e.get("NSExceptionAllowsInsecureHTTPLoads"))
                    || Boolean.TRUE.equals(e.get("NSTemporaryExceptionAllowsInsecureHTTPLoads")))) return true;
        }
        return false;
    }
}
