package com.ota.platform.web.admin;

import com.ota.platform.admin.AccessService;
import com.ota.platform.app.OtaApp;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * 数据大屏：激活、设备、口令、发布、代理和最新动态一屏看全，适合投到电视或第二块显示器。
 * 看哪个范围写在地址里，几块屏幕可以各看各的、收藏下来下次直接打开：
 * /admin/screen 是当前 App，?app=标识 是指定的 App，?scope=all 是当前管理员能看到的全部 App 合在一起（带 App 排行）。
 * 页面带「实时」标记，开着时每 10 秒取一次数据；数据怎么算的见 ScreenData，分享出去的见 ShareController。
 */
@Controller
@RequestMapping("/admin")
public class ScreenController {

    private final AdminShell shell;
    private final AccessService access;
    private final ScreenData data;
    private final ScreenView view;

    public ScreenController(AdminShell shell, AccessService access, ScreenData data, ScreenView view) {
        this.shell = shell;
        this.access = access;
        this.data = data;
        this.view = view;
    }

    @GetMapping("/screen")
    public String screen(@RequestParam(required = false) String app, @RequestParam(required = false) String scope,
                         Model model, HttpSession session) {
        OtaApp current = shell.current(session);
        if (current == null) return "redirect:/admin/apps";
        List<OtaApp> visible = shell.visible();
        boolean all = "all".equals(scope);
        // 只能看到一个 App 时「全部」和它是一回事
        if (all && visible.size() < 2) return "redirect:/admin/screen";
        OtaApp viewed = current;
        if (!all && app != null && !app.isBlank()) {
            String slug = app.trim();
            viewed = visible.stream().filter(a -> a.getSlug().equals(slug)).findFirst().orElse(null);
            // 没有这个 App，或者没有权限看它
            if (viewed == null) return "redirect:/admin/screen";
        }
        view.fill(model, access.current(), visible, current, all ? null : viewed);
        model.addAttribute("scPicks", data.picks(visible));
        // 回后台时接着看正在看的这个 App
        model.addAttribute("scBack", all || viewed.getId().equals(current.getId()) ? "/admin"
                : "/admin/use?app=" + viewed.getSlug() + "&back=/admin");
        return "admin/screen";
    }
}
