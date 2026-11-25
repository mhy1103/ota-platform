package com.ota.platform.installer;

import com.ota.platform.common.ApiException;
import com.ota.platform.config.OtaProperties;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 安装包分片上传：反向代理（宝塔的 nginx 默认 50MB）限制了单个请求的大小，大的 IPA 一次传不上去。
 * 浏览器和命令行把文件切成几 MB 一片，按顺序传到临时文件里，传完再当成一个文件处理。
 * 同一片重传（网络断了重试）会覆盖原来那片；一小时没动静的上传自动清掉。
 */
@Component
public class ChunkedUploads {

    public static final long MAX_SIZE = 1024L * 1024 * 1024;
    private static final Duration IDLE = Duration.ofHours(1);

    private final OtaProperties props;
    private final Map<String, Upload> uploads = new ConcurrentHashMap<>();

    public ChunkedUploads(OtaProperties props) {
        this.props = props;
    }

    private static final class Upload {
        final String owner;
        final Long appId;
        final String fileName;
        final long size;
        final Path file;
        long received;
        Instant touched = Instant.now();

        Upload(String owner, Long appId, String fileName, long size, Path file) {
            this.owner = owner;
            this.appId = appId;
            this.fileName = fileName;
            this.size = size;
            this.file = file;
        }
    }

    public record Done(Path file, String fileName) {}

    public String start(String owner, Long appId, String fileName, long size) throws IOException {
        return start(owner, appId, fileName, size, "apk|ipa", "只支持 .apk 和 .ipa 文件", MAX_SIZE, "安装包最大 1GB");
    }

    /** exts：允许的扩展名（正则，比如 "zip|otaproj"） */
    public String start(String owner, Long appId, String fileName, long size, String exts, String badFile, long maxSize, String tooLarge)
            throws IOException {
        sweep();
        String name = fileName == null ? "" : Path.of(fileName.trim()).getFileName().toString();
        if (!name.toLowerCase(Locale.ROOT).matches(".+\\.(" + exts + ")")) throw ApiException.badRequest("BAD_FILE", badFile);
        if (size <= 0) throw ApiException.badRequest("BAD_SIZE", "文件是空的");
        if (size > maxSize) throw ApiException.badRequest("TOO_LARGE", tooLarge);
        Path dir = Files.createDirectories(Path.of(props.getDataDir(), "tmp", "uploads"));
        String id = UUID.randomUUID().toString();
        Path file = Files.createFile(dir.resolve(id + ".part"));
        uploads.put(id, new Upload(owner, appId, name, size, file));
        return id;
    }

    /** 写入从 offset 开始的一片，返回已经收到的字节数 */
    public long append(String id, String owner, Long appId, long offset, InputStream body) throws IOException {
        Upload u = get(id, owner, appId);
        synchronized (u) {
            if (offset < 0 || offset > u.received) {
                throw ApiException.badRequest("BAD_OFFSET", "分片位置不对：已经收到 " + u.received + " 字节");
            }
            try (FileChannel ch = FileChannel.open(u.file, StandardOpenOption.WRITE)) {
                ch.truncate(offset);
            }
            long n = offset;
            try (OutputStream out = Files.newOutputStream(u.file, StandardOpenOption.APPEND)) {
                byte[] buf = new byte[64 * 1024];
                for (int r; (r = body.read(buf)) > 0; ) {
                    n += r;
                    if (n > u.size) throw ApiException.badRequest("TOO_LARGE", "收到的内容比文件大小还多");
                    out.write(buf, 0, r);
                }
            }
            u.received = n;
            u.touched = Instant.now();
            return n;
        }
    }

    /** 传完了：返回临时文件，用完由调用方删掉 */
    public Done finish(String id, String owner, Long appId) {
        Upload u = get(id, owner, appId);
        synchronized (u) {
            if (u.received != u.size) {
                throw ApiException.badRequest("INCOMPLETE", "还没传完：" + u.received + " / " + u.size + " 字节");
            }
            uploads.remove(id);
            return new Done(u.file, u.fileName);
        }
    }

    private Upload get(String id, String owner, Long appId) {
        Upload u = id == null ? null : uploads.get(id);
        if (u == null || !u.owner.equals(owner) || !u.appId.equals(appId)) {
            throw ApiException.notFound("NO_UPLOAD", "上传已经过期或不存在，请重新上传");
        }
        return u;
    }

    private void sweep() {
        Instant limit = Instant.now().minus(IDLE);
        uploads.entrySet().removeIf(e -> {
            if (e.getValue().touched.isAfter(limit)) return false;
            try {
                Files.deleteIfExists(e.getValue().file);
            } catch (IOException ignored) {
                // 删不掉的临时文件不影响上传
            }
            return true;
        });
    }
}
