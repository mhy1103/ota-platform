package com.ota.platform.web.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ota.platform.admin.AccessHelper;
import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.app.OtaApp;
import com.ota.platform.web.AdminUi;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.domain.Page;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

/**
 * 页面数据：控制器返回的页面都转成 JSON（页面名、模型、上下文），浏览器里的 Vue 渲染。
 * 只发页面上用到的字段（vue-fields.json，前端打包时从组件里统计出来），密码、密钥这类字段永远不发；
 * 模型里总带着、页面只在有权限时才显示的值，没有权限时也不发。
 */
@Configuration
public class PageData {

    public static final String HEADER = "X-OTA-Data";
    private static final Logger log = LoggerFactory.getLogger(PageData.class);
    private static final Pattern SECRET = Pattern.compile("(?i).*(password|secret|hash|recovery|privatekey|salt).*|.*Enc");

    private final AdminUi ui;
    private final AccessHelper accessHelper;
    private final AccessService access;
    private final VueUi shell;
    private final BuildStamp build;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Class<?>, Map<String, Optional<Accessor>>> accessors = new ConcurrentHashMap<>();
    private volatile Map<String, Fields> views;

    public PageData(AdminUi ui, AccessHelper accessHelper, AccessService access, VueUi shell, BuildStamp build) {
        this.build = build;
        this.ui = ui;
        this.accessHelper = accessHelper;
        this.access = access;
        this.shell = shell;
    }

    record Fields(Set<String> model, Set<String> props) {}

    private interface Accessor {
        Object get(Object target) throws Exception;
    }

    /** 所有页面都转成 JSON；浏览器直接打开页面时由 VueUi 给加载壳，脚本再带 X-OTA-Data 头来取 */
    @Bean
    public ViewResolver pageDataViewResolver() {
        return new PageDataResolver();
    }

    private final class PageDataResolver implements ViewResolver, Ordered {
        @Override
        public View resolveViewName(String viewName, java.util.Locale locale) {
            if (viewName.startsWith("redirect:") || viewName.startsWith("forward:")) return null;
            Fields f = views().get(viewName);
            return f == null ? null : new JsonView(viewName, f);
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    private final class JsonView implements View {
        private final String name;
        private final Fields fields;

        JsonView(String name, Fields fields) {
            this.name = name;
            this.fields = fields;
        }

        /** 按页面参与内容协商，出错时 Spring 的错误处理也会走到错误页而不是它自己的 JSON；实际写出的是 JSON */
        @Override
        public String getContentType() {
            return "text/html";
        }

        @Override
        public void render(Map<String, ?> model, HttpServletRequest req, HttpServletResponse res) throws Exception {
            if (VueUi.browser(req)) {
                shell.shell(res, res.getStatus());
                return;
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("view", name);
            out.put("model", model(model, fields));
            out.put("ctx", ctx(req));
            byte[] body = json.writeValueAsBytes(out);
            res.setContentType("application/json;charset=UTF-8");
            res.setHeader("Cache-Control", "no-store");
            res.setHeader("Vary", HEADER + ", Accept-Encoding");
            res.setHeader(BuildStamp.HEADER, build.id());
            // 服务端的压缩只开给页面和脚本样式（接口 JSON 原样返回），页面数据在这里自己压缩
            String accept = req.getHeader("Accept-Encoding");
            if (body.length >= 4096 && accept != null && accept.contains("gzip")) {
                body = gzip(body);
                res.setHeader("Content-Encoding", "gzip");
            }
            res.setContentLength(body.length);
            res.getOutputStream().write(body);
        }
    }

    private static byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream(data.length / 4);
        try (GZIPOutputStream out = new GZIPOutputStream(buf)) {
            out.write(data);
        }
        return buf.toByteArray();
    }

    // ---------- 模型 ----------

    private Map<String, Object> model(Map<String, ?> model, Fields f) {
        Map<String, Object> out = new LinkedHashMap<>();
        boolean canSettings = Boolean.TRUE.equals(model.get("canSettings"));
        for (String key : f.model()) {
            if (!model.containsKey(key)) continue;
            Object v = model.get(key);
            if ((key.equals("codeSecret") || key.equals("webhookSecret")) && !canSettings) v = null;
            out.put(key, value(v, f.props(), 0));
        }
        return out;
    }

    private Object value(Object v, Set<String> props, int depth) {
        if (v == null || depth > 16) return null;
        if (v instanceof String || v instanceof Boolean || v instanceof Integer || v instanceof Long
                || v instanceof Short || v instanceof Byte || v instanceof BigInteger) return v;
        if (v instanceof Double || v instanceof Float || v instanceof BigDecimal) return Map.of("$d", v.toString());
        if (v instanceof Number n) return n.doubleValue() == n.longValue() ? n.longValue() : Map.of("$d", n.toString());
        if (v instanceof Character || v instanceof CharSequence) return v.toString();
        if (v instanceof Instant i) return time(i.toEpochMilli(), i.toString());
        if (v instanceof LocalDateTime l) return time(l.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), l.toString());
        if (v instanceof ZonedDateTime z) return time(z.toInstant().toEpochMilli(), z.toString());
        if (v instanceof OffsetDateTime o) return time(o.toInstant().toEpochMilli(), o.toString());
        if (v instanceof Date d) return time(d.getTime(), d.toString());
        if (v instanceof TemporalAccessor) return v.toString();
        if (v instanceof Enum<?> e) return enumValue(e, props, depth);
        if (v instanceof Optional<?> o) return value(o.orElse(null), props, depth);
        if (v instanceof Page<?> p) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("content", value(p.getContent(), props, depth + 1));
            m.put("totalPages", p.getTotalPages());
            m.put("totalElements", p.getTotalElements());
            m.put("number", p.getNumber());
            m.put("size", p.getSize());
            m.put("numberOfElements", p.getNumberOfElements());
            m.put("first", p.isFirst());
            m.put("last", p.isLast());
            return m;
        }
        if (v instanceof Map<?, ?> map) {
            List<Object> entries = new ArrayList<>(map.size());
            for (Map.Entry<?, ?> e : map.entrySet()) {
                Object k = e.getKey() instanceof Enum<?> en ? en.name() : String.valueOf(e.getKey());
                entries.add(Arrays.asList(k, value(e.getValue(), props, depth + 1)));
            }
            return Map.of("$m", entries);
        }
        if (v instanceof Map.Entry<?, ?> e) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", value(e.getKey(), props, depth + 1));
            m.put("value", value(e.getValue(), props, depth + 1));
            return m;
        }
        if (v instanceof Iterable<?> it) {
            List<Object> list = new ArrayList<>(v instanceof Collection<?> c ? c.size() : 16);
            for (Object x : it) list.add(value(x, props, depth + 1));
            return list;
        }
        if (v.getClass().isArray()) {
            int n = Array.getLength(v);
            List<Object> list = new ArrayList<>(n);
            for (int i = 0; i < n; i++) list.add(value(Array.get(v, i), props, depth + 1));
            return list;
        }
        Class<?> type = v.getClass();
        String pkg = type.getPackageName();
        if (pkg.startsWith("java.") || pkg.startsWith("javax.") || pkg.startsWith("jakarta.") || pkg.startsWith("org.springframework.")) {
            return v.toString();
        }
        Map<String, Object> obj = new LinkedHashMap<>();
        for (String name : props) {
            Optional<Accessor> a = accessor(type, name);
            if (a.isEmpty()) continue;
            Object raw;
            try {
                raw = a.get().get(v);
            } catch (Exception e) {
                continue;
            }
            // 名字像密码、密钥的字段不发；只是开关的（比如 mustChangePassword「要修改初始密码」）照发
            if (SECRET.matcher(name).matches() && !(raw instanceof Boolean)) continue;
            obj.put(name, value(redact(v, name, raw), props, depth + 1));
        }
        return obj;
    }

    private static Map<String, Object> time(long ms, String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("$t", ms);
        m.put("s", text);
        return m;
    }

    private Object enumValue(Enum<?> e, Set<String> props, int depth) {
        Map<String, Object> extra = new LinkedHashMap<>();
        for (String name : props) {
            if (name.equals("name") || SECRET.matcher(name).matches()) continue;
            Optional<Accessor> a = accessor(e.getDeclaringClass(), name);
            if (a.isEmpty()) continue;
            try {
                extra.put(name, value(a.get().get(e), props, depth + 1));
            } catch (Exception ignored) {
                // 取不到的字段不发
            }
        }
        String text = e.toString();
        if (extra.isEmpty() && text.equals(e.name())) return e.name();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("$e", e.name());
        if (!text.equals(e.name())) m.put("$s", text);
        m.putAll(extra);
        return m;
    }

    /** 群机器人、Webhook 地址里带着对方的令牌：没有「App 设置」权限的人只告诉他有没有配置 */
    private Object redact(Object owner, String name, Object value) {
        if (value != null && owner instanceof OtaApp app && (name.equals("webhookUrl") || name.equals("notifyUrl"))
                && !access.can(app, AppPerm.SETTINGS)) {
            return "•";
        }
        return value;
    }

    private Optional<Accessor> accessor(Class<?> type, String name) {
        return accessors.computeIfAbsent(type, t -> new ConcurrentHashMap<>()).computeIfAbsent(name, n -> find(type, n));
    }

    private static Optional<Accessor> find(Class<?> type, String name) {
        if (type.isRecord()) {
            for (RecordComponent rc : type.getRecordComponents()) {
                if (rc.getName().equals(name)) return Optional.of(invoker(rc.getAccessor()));
            }
        }
        String cap = Character.toUpperCase(name.charAt(0)) + name.substring(1);
        for (String m : List.of("get" + cap, "is" + cap, name)) {
            Method method = method(type, m);
            if (method == null) continue;
            if (m.startsWith("is") && !m.equals(name) && method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class) continue;
            return Optional.of(invoker(method));
        }
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                if (Modifier.isPublic(f.getModifiers()) && !Modifier.isStatic(f.getModifiers())) return Optional.of(f::get);
            } catch (NoSuchFieldException ignored) {
                // 往父类找
            }
        }
        return Optional.empty();
    }

    private static Method method(Class<?> type, String name) {
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod(name);
                if (Modifier.isPublic(m.getModifiers()) && !Modifier.isStatic(m.getModifiers()) && m.getReturnType() != void.class) return m;
            } catch (NoSuchMethodException ignored) {
                // 往父类找
            }
        }
        try {
            Method m = type.getMethod(name);
            return Modifier.isStatic(m.getModifiers()) || m.getReturnType() == void.class ? null : m;
        } catch (NoSuchMethodException e) {
            return null;
        }
    }

    private static Accessor invoker(Method m) {
        m.setAccessible(true);
        return m::invoke;
    }

    // ---------- 上下文：时区、当前时间、请求参数、权限、样式文件地址 ----------

    private Map<String, Object> ctx(HttpServletRequest req) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("now", System.currentTimeMillis());
        ctx.put("zone", ui.zoneId());
        List<Object> params = new ArrayList<>();
        req.getParameterMap().forEach((k, v) -> params.add(List.of(k, List.of(v))));
        ctx.put("param", Map.of("$m", params));
        Map<String, ?> flash = org.springframework.web.servlet.support.RequestContextUtils.getInputFlashMap(req);
        if (flash != null && flash.get(RedirectHash.KEY) instanceof String h) ctx.put("hash", h);
        Map<String, Object> acc = new LinkedHashMap<>();
        CurrentAdmin me = accessHelper.me();
        if (me != null) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", me.id());
            m.put("username", me.username());
            m.put("name", me.name());
            m.put("owner", me.owner());
            m.put("manageAgents", me.manageAgents());
            m.put("manageCodes", me.manageCodes());
            m.put("guide", me.guide());
            m.put("canManageAgents", me.canManageAgents());
            m.put("canCodes", me.canCodes());
            m.put("roleLabel", me.roleLabel());
            acc.put("me", m);
            List<Object> grants = new ArrayList<>();
            me.grants().forEach((appId, perms) -> grants.add(List.of(String.valueOf(appId), perms.stream().map(Enum::name).sorted().toList())));
            acc.put("grants", Map.of("$m", grants));
        }
        acc.put("owner", accessHelper.owner());
        acc.put("agents", accessHelper.agents());
        acc.put("guide", accessHelper.guide());
        Map<String, Object> perms = new LinkedHashMap<>();
        for (AppPerm p : AppPerm.values()) perms.put(p.name().toLowerCase(), accessHelper.can(p.name().toLowerCase()));
        perms.put("codes", accessHelper.codes());
        acc.put("perms", perms);
        ctx.put("access", acc);
        return ctx;
    }

    // ---------- 字段清单 ----------

    private Map<String, Fields> views() {
        Map<String, Fields> v = views;
        if (v != null) return v;
        Map<String, Fields> loaded = new LinkedHashMap<>();
        ClassPathResource res = new ClassPathResource("vue-fields.json");
        if (!res.exists()) throw new IllegalStateException("没有 vue-fields.json，请用 ./build.sh 打包");
        {
            try (InputStream in = res.getInputStream()) {
                JsonNode root = json.readTree(in).path("views");
                root.fields().forEachRemaining(e -> loaded.put(e.getKey(),
                        new Fields(names(e.getValue().path("model")), names(e.getValue().path("props")))));
            } catch (Exception e) {
                log.warn("读取 vue-fields.json 失败：{}", e.getMessage());
            }
        }
        views = loaded;
        return loaded;
    }

    private static Set<String> names(JsonNode arr) {
        Set<String> out = new HashSet<>();
        arr.forEach(n -> out.add(n.asText()));
        return Set.copyOf(out);
    }
}
