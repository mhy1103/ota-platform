package com.ota.platform.web.admin;

import com.ota.platform.app.OtaApp;
import com.ota.platform.common.Hashes;
import com.ota.platform.config.OtaProperties;
import com.ota.platform.settings.ThemeCatalog;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/** API 文档。示例里的地址按当前配置生成；签名示例用固定的示例密钥计算，可以拿来核对自己的实现。 */
@Controller
@RequestMapping("/admin/docs")
public class AdminDocsController {

    static final String EX_KEY = "app_Q2xpZW50RXhhbXBsZQ";
    static final String EX_SECRET = "example-secret-do-not-use";
    static final String EX_TS = "1794362400000";
    static final String EX_NONCE = "c1f2a9e07b3d4e58";
    static final String EX_BODY = "{\"code\":\"88888\",\"deviceId\":\"6F1C2B7A-4D3E-4B8A-9C21-5E0F7A3D9B12\"}";

    private final AdminShell shell;
    private final OtaProperties props;

    @Value("${spring.servlet.multipart.max-file-size:200MB}")
    private String maxUpload;

    @Value("${server.servlet.session.timeout:30m}")
    private Duration sessionTimeout;

    public AdminDocsController(AdminShell shell, OtaProperties props) {
        this.shell = shell;
        this.props = props;
    }

    @GetMapping
    public String docs(Model model, HttpSession session) {
        OtaApp app = shell.populate(model, session);
        model.addAttribute("baseUrl", props.baseUrl());
        model.addAttribute("slug", app != null ? app.getSlug() : "your-app");
        model.addAttribute("keyId", app != null ? app.getKeyId() : "main");
        model.addAttribute("maxUpload", maxUpload.replaceAll("(?i)(\\d+)\\s*([KMG]B)", "$1 $2"));
        model.addAttribute("maxAsset", props.getMaxAssetBytes() / 1024 / 1024 + " MB");
        model.addAttribute("sessionTimeout", human(sessionTimeout));
        model.addAttribute("themes", ThemeCatalog.THEMES);
        model.addAttribute("patchBases", props.getPatch().getBases());
        model.addAttribute("patchRatio", Math.round(props.getPatch().getMaxRatio() * 100));

        model.addAttribute("exKey", EX_KEY);
        model.addAttribute("exSecret", EX_SECRET);
        model.addAttribute("exTs", EX_TS);
        model.addAttribute("exNonce", EX_NONCE);
        model.addAttribute("exBody", EX_BODY);
        model.addAttribute("exSig", Hashes.hmacSha256Hex(EX_SECRET.getBytes(StandardCharsets.UTF_8), EX_TS + "\n" + EX_NONCE + "\n" + EX_BODY));
        String whBody = "{\"id\":\"…\",\"event\":\"redemption.confirmed\",\"createdAt\":\"…\",\"data\":{…}}";
        model.addAttribute("exWebhookBody", whBody);
        return "admin/docs";
    }

    private static String human(Duration d) {
        if (d.toDays() > 0 && d.toHours() % 24 == 0) return d.toDays() + " 天";
        if (d.toHours() > 0 && d.toMinutes() % 60 == 0) return d.toHours() + " 小时";
        return d.toMinutes() + " 分钟";
    }
}
