package com.ota.platform.web.admin;

import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.app.OtaApp;
import com.ota.platform.audit.AppContext;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/** 数据大屏页面的数据（sc 开头的模型字段）：后台打开的和分享出去的用同一份，看到的一模一样 */
@Component
public class ScreenView {

    private final ScreenData data;
    private final TodoService todos;

    public ScreenView(ScreenData data, TodoService todos) {
        this.data = data;
        this.todos = todos;
    }

    /**
     * visible：这个管理员能看到的 App；viewed：看的那个 App（看全部时为 null）；current：TA 在后台的当前 App（待处理按它取）。
     * 权限按 me 算：没有「口令与激活」看不到热门口令，不能管代理看不到代理排行
     */
    public void fill(Model model, CurrentAdmin me, List<OtaApp> visible, OtaApp current, OtaApp viewed) {
        boolean all = viewed == null;
        boolean codes = me != null && me.canCodes();
        boolean agents = me != null && me.canManageAgents();
        AppContext.set(all ? null : viewed.getId());
        model.addAllAttributes(data.snapshot(all ? ScreenData.Scope.all(visible) : ScreenData.Scope.of(viewed), codes, agents));
        model.addAttribute("scScope", all ? "all" : "app");
        model.addAttribute("scName", all ? null : viewed.getName());
        model.addAttribute("scSlug", all ? null : viewed.getSlug());
        model.addAttribute("scAppId", all ? null : viewed.getId());
        model.addAttribute("scIcon", all ? null : viewed.getIconVersion());
        model.addAttribute("scFace", all ? null : viewed.isRedeemOpen() ? "B" : "A");
        model.addAttribute("scCodesOn", codes);
        model.addAttribute("scAgentsOn", agents);

        // 待处理：看一个 App 时只列它的和不属于哪个 App 的，看全部 App 时都列；启动失败偏多的版本单独数出来，App 排行里也标上
        List<TodoService.Todo> list = all ? todos.all(current) : todos.of(viewed, current);
        List<TodoService.Todo> alarms = list.stream().filter(t -> "alarm".equals(t.kind())).toList();
        model.addAttribute("scTodos", list.stream().limit(8).toList());
        model.addAttribute("scTodoCount", list.size());
        model.addAttribute("scAlarms", alarms.size());
        model.addAttribute("scAlarmApps", alarms.stream().map(TodoService.Todo::appId).distinct().toList());
    }
}
