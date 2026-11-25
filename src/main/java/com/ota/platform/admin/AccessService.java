package com.ota.platform.admin;

import com.ota.platform.app.OtaApp;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import java.util.List;
import java.util.Map;

/** 当前管理员是谁、能看哪些 App、在 App 上能做什么 */
@Service
public class AccessService {

    static final String ATTR = "ota.admin";

    private final AdminUserService users;

    public AccessService(AdminUserService users) {
        this.users = users;
    }

    /** 当前登录的管理员；代理、未登录、已停用返回 null */
    public CurrentAdmin current() {
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null && ra.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST) instanceof CurrentAdmin c) return c;
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return null;
        CurrentAdmin c = load(a.getName());
        if (c != null && ra != null) ra.setAttribute(ATTR, c, RequestAttributes.SCOPE_REQUEST);
        return c;
    }

    /**
     * 这次请求里按 username 这个管理员的身份算（分享出去的数据大屏按分享人现在的权限取数），算完恢复。
     * body 拿到的是这个管理员；账号停用、删了时是 null
     */
    public <T> T actAs(String username, java.util.function.Function<CurrentAdmin, T> body) {
        CurrentAdmin c = load(username);
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra == null) return body.apply(null);
        Object before = ra.getAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
        if (c != null) ra.setAttribute(ATTR, c, RequestAttributes.SCOPE_REQUEST);
        else ra.removeAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
        try {
            return body.apply(c);
        } finally {
            if (before != null) ra.setAttribute(ATTR, before, RequestAttributes.SCOPE_REQUEST);
            else ra.removeAttribute(ATTR, RequestAttributes.SCOPE_REQUEST);
        }
    }

    public CurrentAdmin load(String username) {
        return users.find(username).filter(AdminUser::isEnabled)
                .map(u -> new CurrentAdmin(u.getId(), u.getUsername(), u.getName(), u.isOwner(), u.isManageAgents(), u.isManageCodes(),
                        u.isOwner() ? Map.of() : users.grantsOf(u.getId()), u.getGuide() == null ? "" : u.getGuide()))
                .orElse(null);
    }

    public List<OtaApp> visible(List<OtaApp> all) {
        CurrentAdmin me = current();
        if (me == null) return List.of();
        return me.owner() ? all : all.stream().filter(a -> me.canSee(a.getId())).toList();
    }

    public boolean can(OtaApp app, AppPerm perm) {
        CurrentAdmin me = current();
        return me != null && app != null && me.can(app.getId(), perm);
    }

    public boolean owner() {
        CurrentAdmin me = current();
        return me != null && me.owner();
    }
}
