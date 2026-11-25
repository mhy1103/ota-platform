package com.ota.platform.web.admin;

import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.AppPerm;
import com.ota.platform.app.AppService;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AuditService;
import com.ota.platform.common.ApiException;
import com.ota.platform.signing.PemSupport;
import com.ota.platform.signing.SigningInspector;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;

/** 当前 App 的签名配置。私钥只能上传，不提供下载。 */
@Controller
@RequestMapping("/admin/signing")
public class AdminSigningController {

    private static final int MAX_PEM_BYTES = 32 * 1024;

    private final AppService apps;
    private final SigningInspector inspector;
    private final AdminShell shell;
    private final AuditService audit;
    private final AccessService access;
    private final com.ota.platform.config.OtaProperties props;

    public AdminSigningController(AppService apps, SigningInspector inspector, AdminShell shell, AuditService audit, AccessService access,
                                  com.ota.platform.config.OtaProperties props) {
        this.props = props;
        this.apps = apps;
        this.inspector = inspector;
        this.shell = shell;
        this.audit = audit;
        this.access = access;
    }

    /** 复用来源：有签名权限的其他 App，以及它和当前 App 是不是已经是同一对密钥 */
    public record Source(OtaApp app, boolean same) {}

    @GetMapping
    public String page(Model model, HttpSession session) {
        OtaApp app = shell.populate(model, session);
        if (app == null) return "redirect:/admin/apps";
        model.addAttribute("s", model.getAttribute("signing"));
        String mine = keyPrint(app);
        List<Source> sources = new ArrayList<>();
        for (OtaApp a : shell.visible()) {
            if (a.getId().equals(app.getId()) || !access.can(a, AppPerm.SIGNING)) continue;
            sources.add(new Source(a, mine != null && mine.equals(keyPrint(a))));
        }
        model.addAttribute("sources", sources);
        model.addAttribute("sharedWith", sources.stream().filter(Source::same).map(x -> x.app().getName()).toList());
        model.addAttribute("canSigning", access.can(app, AppPerm.SIGNING));
        model.addAttribute("manifestUrl", props.baseUrl() + "/m/" + app.getSlug());
        return "admin/signing";
    }

    /**
     * 复用另一个 App 的签名密钥（复制一份，之后两边各自独立）。适合同一套代码打出的多个 App 内置同一张证书。
     * 要在两个 App 上都有「签名与备份」权限，避免把别的 App 的私钥复制到自己能下载备份的 App 里
     */
    @PostMapping("/reuse")
    public String reuse(@RequestParam String source, HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        OtaApp from = apps.find(source).orElse(null);
        if (from == null || !access.can(app, AppPerm.SIGNING) || !access.can(from, AppPerm.SIGNING)) {
            ra.addFlashAttribute("error", "要在两个 App 上都有「签名与备份」权限才能复用签名密钥");
            return "redirect:/admin/signing";
        }
        AppService.SigningMaterial m = apps.signingMaterial(from);
        apps.replaceSigningKey(app, m.keyId(), m.privateKeyPem(), m.certificatePem());
        SigningInspector.Status s = inspector.inspect(apps.require(app.getSlug()));
        audit.log("SIGNING_UPDATE", app.getSlug() + " 复用 " + from.getSlug() + " 的签名密钥（keyid " + m.keyId() + "），状态：" + s.getHeadline());
        ra.addFlashAttribute("ok".equals(s.getLevel()) ? "msg" : "error",
                "已复用 " + from.getName() + " 的签名密钥，keyid 是 " + m.keyId() + "。已安装、内置旧证书的 App 收不到用新密钥签名的更新");
        return "redirect:/admin/signing";
    }

    private static String keyPrint(OtaApp a) {
        try {
            return PemSupport.fingerprint(PemSupport.parseCertificate(a.getCertificatePem()).getPublicKey().getEncoded());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 上传私钥和/或证书。只传一个时，它必须和现有的另一半配对；整套更换时两个一起传。
     * 证书编进了 App，换了证书，已经装好的 App 就收不到更新了。
     */
    @PostMapping("/upload")
    public String upload(@RequestParam(value = "privateKey", required = false) MultipartFile privateKey,
                         @RequestParam(value = "certificate", required = false) MultipartFile certificate,
                         HttpSession session, RedirectAttributes ra) {
        OtaApp app = shell.current(session);
        if (app == null) return "redirect:/admin/apps";
        try {
            String key = text(privateKey);
            String cert = text(certificate);
            if (key == null && cert == null) throw new IllegalArgumentException("请选择要上传的私钥或证书文件");
            String keyPem = key != null ? key : PemSupport.toPem("PRIVATE KEY", apps.privateKey(app).getEncoded());
            String certPem = cert != null ? cert : app.getCertificatePem();
            apps.replaceSigningKey(app, null, keyPem, certPem);
            OtaApp updated = apps.require(app.getSlug());
            SigningInspector.Status s = inspector.inspect(updated);
            String what = key != null && cert != null ? "私钥和证书" : key != null ? "私钥" : "证书";
            String fp = s.getKeyFingerprint() != null ? s.getKeyFingerprint().substring(0, 23) : "—";
            audit.log("SIGNING_UPDATE", app.getSlug() + " 更新签名" + what + "，公钥指纹 " + fp + "，状态：" + s.getHeadline());
            ra.addFlashAttribute("ok".equals(s.getLevel()) ? "msg" : "error", "已保存" + what + "。" + s.getHeadline());
        } catch (ApiException | IllegalArgumentException e) {
            ra.addFlashAttribute("error", "未保存：" + e.getMessage());
        } catch (Exception e) {
            ra.addFlashAttribute("error", "保存失败：" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
        return "redirect:/admin/signing";
    }

    @GetMapping("/certificate.pem")
    public ResponseEntity<byte[]> certificate(HttpSession session) {
        OtaApp app = shell.current(session);
        if (app == null) return ResponseEntity.notFound().build();
        return pem(app.getCertificatePem().getBytes(StandardCharsets.US_ASCII), app.getSlug() + "-certificate.pem");
    }

    @GetMapping("/public-key.pem")
    public ResponseEntity<byte[]> publicKey(HttpSession session) throws Exception {
        OtaApp app = shell.current(session);
        if (app == null) return ResponseEntity.notFound().build();
        PublicKey pub = PemSupport.parseCertificate(app.getCertificatePem()).getPublicKey();
        return pem(PemSupport.toPem("PUBLIC KEY", pub.getEncoded()).getBytes(StandardCharsets.US_ASCII), app.getSlug() + "-public-key.pem");
    }

    private static ResponseEntity<byte[]> pem(byte[] body, String name) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .contentType(MediaType.parseMediaType("application/x-pem-file"))
                .body(body);
    }

    private static String text(MultipartFile f) throws Exception {
        if (f == null || f.isEmpty()) return null;
        if (f.getSize() > MAX_PEM_BYTES) throw new IllegalArgumentException("文件过大，签名文件应是几 KB 的 .pem 文本文件");
        return new String(f.getBytes(), StandardCharsets.UTF_8);
    }
}
