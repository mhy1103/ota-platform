package com.ota.platform.web.admin;

import com.ota.platform.admin.AccessService;
import com.ota.platform.admin.CurrentAdmin;
import com.ota.platform.agent.AgentService;
import com.ota.platform.web.Prefs;
import com.ota.platform.web.page.BuildStamp;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 后台开着时每 30 秒问一次：版本号（变了提示重新加载）、待处理（顶栏铃铛和侧栏数字不用刷新页面就更新）、
 *  看板布局指纹（在别的设备上调过看板，开着的概览跟着换） */
@RestController
public class AdminPulseController {

    private final BuildStamp build;
    private final TodoService todos;
    private final AdminShell shell;
    private final AccessService access;
    private final AgentService agents;
    private final Prefs prefs;

    public AdminPulseController(BuildStamp build, TodoService todos, AdminShell shell, AccessService access, AgentService agents, Prefs prefs) {
        this.build = build;
        this.todos = todos;
        this.shell = shell;
        this.access = access;
        this.agents = agents;
        this.prefs = prefs;
    }

    @GetMapping("/admin/pulse")
    public ResponseEntity<Map<String, Object>> pulse(HttpSession session) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("build", build.id());
        out.put("time", build.time().toString());
        List<TodoService.Todo> list = todos.all(shell.current(session));
        Map<String, Object> todo = new LinkedHashMap<>();
        todo.put("count", list.size());
        todo.put("level", list.isEmpty() ? null : list.get(0).level());
        todo.put("items", list.stream().limit(200).toList());
        out.put("todo", todo);
        CurrentAdmin me = access.current();
        out.put("requests", me != null && me.canManageAgents() ? agents.pendingCount() : 0);
        if (me != null) out.put("board", prefs.boardSigOf(me.id()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(out);
    }
}
