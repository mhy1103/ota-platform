package com.ota.platform.web.page;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.ServletContext;
import jakarta.websocket.CloseReason;
import jakarta.websocket.DeploymentException;
import jakarta.websocket.Endpoint;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.HandshakeResponse;
import jakarta.websocket.MessageHandler;
import jakarta.websocket.Session;
import jakarta.websocket.server.HandshakeRequest;
import jakarta.websocket.server.ServerContainer;
import jakarta.websocket.server.ServerEndpointConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.context.ServletContextAware;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * 开着的后台页面连一条 WebSocket（/app/live），定时查新版本、待处理、看板刷新这些后台请求都从这里走：
 * 安卓上的 UC、QQ、夸克、华为、小米浏览器和微信里，页面每发一个 fetch 地址栏下面都会走一遍加载进度，WebSocket 上的消息不会。
 * <p>
 * 页面发 {"i":1,"u":"/admin/pulse","k":"json"}，这里带着建立连接时浏览器给的 Cookie 在本机请求一次这个地址，把结果原样发回去，
 * 所以登录状态、权限、两步验证、演示环境都和浏览器直接请求一模一样，连接本身不另外认证。
 * 只转发后台、代理端页面、分享出去的数据大屏和版本号的 GET；只认本站页面发起的连接，别的网站拿不到这个页面的数据。
 */
@Component
public class LiveSocket implements ServletContextAware, SmartInitializingSingleton, ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(LiveSocket.class);
    public static final String PATH = "/app/live";
    private static final Pattern ALLOWED = Pattern.compile("^(/admin|/agent)(/[^\\s#]*)?(\\?[^\\s#]*)?$|^/app/version$|^/s/[A-Za-z0-9_-]{8,64}$");
    private static final String HEADERS = "ota.live.headers", SAME_SITE = "ota.live.sameSite";
    /** 浏览器请求里要原样带过去的头：Cookie 是登录状态，其余影响语言、显示的 IP */
    private static final List<String> FORWARD = List.of("cookie", "user-agent", "accept-language", "x-forwarded-for", "x-real-ip", "x-forwarded-proto");
    private static final long IDLE_MS = 5 * 60_000;
    private static final int MAX_BUSY = 6, MAX_BODY = 4 * 1024 * 1024, MAX_HOPS = 5;

    private final Environment env;
    private final BuildStamp build;
    private final ObjectMapper json = new ObjectMapper();
    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "ota-live");
        t.setDaemon(true);
        return t;
    });
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(3))
            .executor(pool)
            .build();
    private ServletContext servletContext;
    private volatile String base;

    public LiveSocket(Environment env, BuildStamp build) {
        this.env = env;
        this.build = build;
    }

    @Override
    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    /** 页面还没开始接请求时注册；不是 Tomcat、服务自己开了 HTTPS 时不注册，页面照常用 fetch */
    @Override
    public void afterSingletonsInstantiated() {
        if (servletContext == null || env.getProperty("server.ssl.enabled", Boolean.class, false)) return;
        if (!(servletContext.getAttribute(ServerContainer.class.getName()) instanceof ServerContainer container)) return;
        try {
            container.addEndpoint(ServerEndpointConfig.Builder.create(Channel.class, PATH).configurator(new Handshake()).build());
        } catch (DeploymentException e) {
            log.warn("页面的 WebSocket 没注册上，后台请求照常用 fetch：{}", e.getMessage());
        }
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if (event.getApplicationContext().getParent() != null) return;
        String addr = env.getProperty("server.address", "").trim();
        if (addr.isEmpty() || addr.equals("0.0.0.0") || addr.equals("::") || addr.equals("[::]")) addr = "127.0.0.1";
        else if (addr.contains(":") && !addr.startsWith("[")) addr = "[" + addr + "]";
        base = "http://" + addr + ":" + event.getWebServer().getPort();
    }

    /** 浏览器打开的页面和连接的地址不是一个站点（别的网站偷偷连过来）就不理 */
    static boolean sameSite(String origin, String host, String forwardedHost) {
        if (origin == null || origin.isBlank()) return true;
        String from;
        try {
            from = URI.create(origin.trim()).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (from == null) return false;
        return from.equalsIgnoreCase(hostOnly(host)) || from.equalsIgnoreCase(hostOnly(forwardedHost));
    }

    static String hostOnly(String hostPort) {
        if (hostPort == null) return "";
        String h = hostPort.split(",")[0].trim();
        if (h.startsWith("[")) {
            int end = h.indexOf(']');
            return end > 0 ? h.substring(1, end) : h;
        }
        int colon = h.indexOf(':');
        return colon >= 0 && h.indexOf(':', colon + 1) < 0 ? h.substring(0, colon) : h;
    }

    static boolean allowed(String path) {
        return path != null && path.length() <= 2048 && ALLOWED.matcher(path).matches() && !path.contains("..");
    }

    /** 跳转到本站的地址才跟着走（比如会话过期跳登录页），返回站内路径；跳到别的网站返回 null */
    static String local(String location, String from, String host) {
        if (location == null || location.isBlank()) return null;
        try {
            URI next = URI.create("http://local" + from).resolve(location.trim());
            String h = next.getHost();
            if (h != null && !h.equals("local") && !h.equals("127.0.0.1") && !h.equalsIgnoreCase("localhost") && !h.equalsIgnoreCase(hostOnly(host))) {
                return null;
            }
            String path = next.getRawPath() == null || next.getRawPath().isEmpty() ? "/" : next.getRawPath();
            return path + (next.getRawQuery() == null ? "" : "?" + next.getRawQuery());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String header(Map<String, List<String>> headers, String name) {
        for (Map.Entry<String, List<String>> e : headers.entrySet()) {
            if (e.getKey() != null && e.getKey().equalsIgnoreCase(name) && e.getValue() != null && !e.getValue().isEmpty()) {
                return String.join(name.equalsIgnoreCase("cookie") ? "; " : ", ", e.getValue());
            }
        }
        return null;
    }

    private final class Handshake extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            Map<String, List<String>> h = request.getHeaders();
            Map<String, String> keep = new LinkedHashMap<>();
            for (String name : FORWARD) {
                String v = header(h, name);
                if (v != null && v.length() < 8192 && v.indexOf('\r') < 0 && v.indexOf('\n') < 0) keep.put(name, v);
            }
            String host = header(h, "host");
            if (host != null) keep.put("x-forwarded-host", host);
            sec.getUserProperties().put(HEADERS, keep);
            sec.getUserProperties().put(SAME_SITE, sameSite(header(h, "origin"), host, header(h, "x-forwarded-host")));
        }

        @Override
        public <T> T getEndpointInstance(Class<T> clazz) {
            return clazz.cast(new Channel());
        }
    }

    private record Result(int status, String path, String type, String build, String body) {}

    public final class Channel extends Endpoint {
        private Session session;
        private Map<String, String> headers = Map.of();
        private final AtomicInteger busy = new AtomicInteger();

        @Override
        @SuppressWarnings("unchecked")
        public void onOpen(Session s, EndpointConfig config) {
            Map<String, Object> props = config.getUserProperties();
            if (!Boolean.TRUE.equals(props.get(SAME_SITE))) {
                close(s, new CloseReason(CloseReason.CloseCodes.VIOLATED_POLICY, "origin"));
                return;
            }
            session = s;
            if (props.get(HEADERS) instanceof Map<?, ?> m) headers = (Map<String, String>) m;
            s.setMaxIdleTimeout(IDLE_MS);
            s.setMaxTextMessageBufferSize(8192);
            s.addMessageHandler(String.class, (MessageHandler.Whole<String>) this::onText);
            ObjectNode hi = json.createObjectNode();
            hi.put("t", "hi");
            hi.put("b", build.id());
            send(hi.toString());
        }

        private void onText(String text) {
            JsonNode m;
            try {
                m = json.readTree(text);
            } catch (Exception e) {
                return;
            }
            if (m == null || !m.has("i")) return;
            long id = m.path("i").asLong();
            String path = m.path("u").asText("");
            String kind = "json".equals(m.path("k").asText()) ? "json" : "page";
            if (base == null || !allowed(path)) {
                reply(id, null);
                return;
            }
            if (busy.incrementAndGet() > MAX_BUSY) {
                busy.decrementAndGet();
                reply(id, null);
                return;
            }
            fetch(path, kind, 0).whenComplete((r, err) -> {
                busy.decrementAndGet();
                reply(id, err == null ? r : null);
            });
        }

        private CompletableFuture<Result> fetch(String path, String kind, int hops) {
            HttpRequest.Builder b;
            try {
                b = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(30)).GET();
            } catch (IllegalArgumentException e) {
                return CompletableFuture.completedFuture(null);
            }
            headers.forEach(b::header);
            if ("json".equals(kind)) b.header("Accept", "application/json").header("X-Requested-With", "XMLHttpRequest");
            else b.header("Accept", "text/html").header(PageData.HEADER, "1");
            return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenCompose(r -> {
                if (r.statusCode() / 100 == 3) {
                    String next = local(r.headers().firstValue("location").orElse(null), path, headers.get("x-forwarded-host"));
                    if (next == null || hops >= MAX_HOPS) return CompletableFuture.completedFuture(null);
                    return fetch(next, kind, hops + 1);
                }
                String body = r.body() == null ? "" : r.body();
                if (body.length() > MAX_BODY) return CompletableFuture.completedFuture(null);
                return CompletableFuture.completedFuture(new Result(r.statusCode(), path,
                        r.headers().firstValue("content-type").orElse(""), r.headers().firstValue(BuildStamp.HEADER).orElse(""), body));
            });
        }

        /** r 为空表示这次没办成，页面会改用 fetch 再请求一次 */
        private void reply(long id, Result r) {
            ObjectNode o = json.createObjectNode();
            o.put("t", "r");
            o.put("i", id);
            if (r == null) {
                o.put("s", 599);
            } else {
                o.put("s", r.status());
                o.put("u", r.path());
                o.put("ct", r.type());
                o.put("b", r.build());
                o.put("d", r.body());
            }
            send(o.toString());
        }

        private void send(String text) {
            Session s = session;
            if (s == null || !s.isOpen()) return;
            synchronized (s) {
                try {
                    s.getBasicRemote().sendText(text);
                } catch (Exception e) {
                    log.debug("页面的 WebSocket 发送失败：{}", e.toString());
                }
            }
        }

        @Override
        public void onError(Session s, Throwable t) {
            log.debug("页面的 WebSocket 出错：{}", t.toString());
        }

        private void close(Session s, CloseReason why) {
            try {
                s.close(why);
            } catch (Exception ignored) {
                // 已经断了
            }
        }
    }
}
