package com.ota.platform.manifest;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Expo-Extra-Params 请求头：App 用 Updates.setExtraParamAsync 设的键值，格式是结构化字段字典 key="value", key2="value2"。
 * 只取字符串值，别的类型和参数（;a=b）跳过
 */
final class ExtraParams {

    /** 崩溃的错误内容：UTF-8 再 base64url。iOS 的 Expo-Fatal-Error 带换行被系统丢掉，Android 普通的启动失败不带，App 自己用它补上 */
    static final String LAUNCH_ERROR = "ota-launch-error";
    /** 崩溃的版本 ID（iOS 的 expo-updates 日志里有，Android 没有） */
    static final String LAUNCH_ERROR_UPDATE = "ota-launch-error-update";

    private static final int MAX_HEADER = 8192;
    private static final int MAX_ENCODED = 6000;
    private static final Pattern UUID = Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private ExtraParams() {}

    static Map<String, String> parse(String header) {
        Map<String, String> out = new LinkedHashMap<>();
        if (header == null || header.length() > MAX_HEADER) return out;
        int i = 0, n = header.length();
        while (i < n) {
            while (i < n && (header.charAt(i) == ' ' || header.charAt(i) == '\t')) i++;
            int k = i;
            while (i < n && keyChar(header.charAt(i))) i++;
            String key = header.substring(k, i);
            String value = null;
            if (i < n && header.charAt(i) == '=' && i + 1 < n && header.charAt(i + 1) == '"') {
                StringBuilder sb = new StringBuilder();
                i += 2;
                boolean closed = false;
                while (i < n) {
                    char c = header.charAt(i++);
                    if (c == '\\' && i < n) sb.append(header.charAt(i++));
                    else if (c == '"') {
                        closed = true;
                        break;
                    } else sb.append(c);
                }
                if (!closed) break;
                value = sb.toString();
            }
            // 跳到下一个成员：参数和不认识的值都略过，引号里的逗号不算
            boolean quoted = false;
            while (i < n && (quoted || header.charAt(i) != ',')) {
                char c = header.charAt(i++);
                if (c == '\\' && quoted && i < n) i++;
                else if (c == '"') quoted = !quoted;
            }
            if (!key.isEmpty() && value != null) out.put(key, value);
            i++;
        }
        return out;
    }

    /** App 补报的崩溃错误内容，解不开就是 null */
    static String launchError(Map<String, String> params) {
        String v = params.get(LAUNCH_ERROR);
        if (v == null || v.isBlank() || v.length() > MAX_ENCODED) return null;
        try {
            byte[] raw = Base64.getUrlDecoder().decode(v.trim());
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(raw)).toString().strip();
            return text.isEmpty() ? null : text;
        } catch (IllegalArgumentException | CharacterCodingException e) {
            return null;
        }
    }

    static String launchErrorUpdate(Map<String, String> params) {
        String v = params.get(LAUNCH_ERROR_UPDATE);
        return v != null && UUID.matcher(v.trim()).matches() ? v.trim().toLowerCase(Locale.ROOT) : null;
    }

    private static boolean keyChar(char c) {
        return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' || c == '*';
    }
}
