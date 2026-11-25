package com.ota.platform.security;

import com.ota.platform.admin.AdminUserRepo;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** 管理员登录记录：成功、失败都记，每个管理员只留最近 10 条 */
@Service
public class LoginHistory {

    public static final int KEEP = 10;

    private final LoginRecordRepo records;
    private final AdminUserRepo admins;

    public LoginHistory(LoginRecordRepo records, AdminUserRepo admins) {
        this.records = records;
        this.admins = admins;
    }

    public void success(Long adminId, String username, HttpServletRequest req) {
        save(adminId, username, true, null, req);
    }

    /** 只记管理员账号的失败；随便输的不存在的账号不记，免得被刷满 */
    public void failure(String username, String reason, HttpServletRequest req) {
        if (username == null || username.isBlank()) return;
        admins.findByUsernameIgnoreCase(username.trim()).ifPresent(a -> save(a.getId(), a.getUsername(), false, reason, req));
    }

    public void failure(Long adminId, String username, String reason, HttpServletRequest req) {
        save(adminId, username, false, reason, req);
    }

    public List<LoginRecord> recent(Long adminId) {
        return records.findTop10ByAdminIdOrderByAtDesc(adminId);
    }

    private void save(Long adminId, String username, boolean ok, String reason, HttpServletRequest req) {
        Devices.Device d = Devices.of(req.getHeader("User-Agent"));
        LoginRecord r = new LoginRecord();
        r.setAdminId(adminId);
        r.setUsername(username.length() > 40 ? username.substring(0, 40) : username);
        r.setSuccess(ok);
        r.setReason(reason);
        r.setDevice(d.label());
        r.setMobile(d.mobile());
        records.save(r);
        List<LoginRecord> top = records.findTop10ByAdminIdOrderByAtDesc(adminId);
        if (top.size() == KEEP) records.deleteByAdminBefore(adminId, top.get(KEEP - 1).getAt());
    }
}
