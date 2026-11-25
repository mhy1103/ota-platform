package com.ota.platform.auth;

import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class TokenService {

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final ApiTokenRepository repo;
    private final OtaProperties props;
    private final SecureRandom random = new SecureRandom();

    public TokenService(ApiTokenRepository repo, OtaProperties props) {
        this.repo = repo;
        this.props = props;
    }

    public record Issued(ApiToken token, String plain) {}

    @Transactional
    public Issued issue(String name, String scope, Long appId) {
        String plain = "ota_" + randomString(32);
        ApiToken token = new ApiToken(name, Hashes.sha256Hex(plain), scope, appId);
        token.setTokenPrefix(plain.substring(0, 8));
        return new Issued(repo.save(token), plain);
    }

    public Optional<ApiToken> find(Long id) {
        return id == null ? Optional.empty() : repo.findById(id);
    }

    public java.util.List<ApiToken> forApp(Long appId) {
        return repo.findByAppIdOrderByCreatedAtDesc(appId);
    }

    public java.util.List<ApiToken> adminTokens() {
        return repo.findByScopeOrderByCreatedAtDesc(ApiToken.SCOPE_ADMIN);
    }

    /** 撤销令牌；最后一个管理令牌不能撤销 */
    @Transactional
    public ApiToken revoke(Long id) {
        ApiToken t = repo.findById(id).orElseThrow(() -> new IllegalArgumentException("令牌不存在"));
        if (ApiToken.SCOPE_ADMIN.equals(t.getScope()) && repo.countByScope(ApiToken.SCOPE_ADMIN) <= 1) {
            throw new IllegalArgumentException("这是最后一个管理令牌，不能撤销");
        }
        repo.delete(t);
        return t;
    }

    @Transactional
    public Optional<TokenPrincipal> authenticate(String plain) {
        if (plain == null || plain.isBlank()) return Optional.empty();
        return repo.findByTokenHash(Hashes.sha256Hex(plain.trim())).map(t -> {
            Instant now = Instant.now();
            if (t.getLastUsedAt() == null || t.getLastUsedAt().isBefore(now.minus(TOUCH_INTERVAL))) {
                t.setLastUsedAt(now);
                repo.save(t);
            }
            return new TokenPrincipal(t.getId(), t.getName(), t.getScope(), t.getAppId());
        });
    }

    /** 用环境变量 OTA_BOOTSTRAP_TOKEN 指定了初始管理令牌时（Docker、CI 无人值守部署）建好它；平时在「系统配置 → 管理令牌」里新建 */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void bootstrap() {
        if (repo.countByScope(ApiToken.SCOPE_ADMIN) > 0) return;
        String configured = props.getBootstrapToken();
        if (configured == null || configured.isBlank()) return;
        repo.save(new ApiToken("初始管理令牌", Hashes.sha256Hex(configured.trim()), ApiToken.SCOPE_ADMIN, null));
        log.info("已用配置里的 bootstrap-token 创建初始管理令牌");
    }

    private String randomString(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        return sb.toString();
    }
}
