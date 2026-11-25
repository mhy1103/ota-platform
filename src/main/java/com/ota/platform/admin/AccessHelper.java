package com.ota.platform.admin;

import com.ota.platform.audit.AppContext;
import org.springframework.stereotype.Component;

/** 模板里判断权限：${@access.can('release')}、${@access.owner()}，没有权限的按钮不显示。can('codes') 不分 App */
@Component("access")
public class AccessHelper {

    private final AccessService access;

    public AccessHelper(AccessService access) {
        this.access = access;
    }

    public CurrentAdmin me() {
        return access.current();
    }

    public boolean owner() {
        CurrentAdmin me = access.current();
        return me != null && me.owner();
    }

    public boolean agents() {
        CurrentAdmin me = access.current();
        return me != null && me.canManageAgents();
    }

    public boolean codes() {
        CurrentAdmin me = access.current();
        return me != null && me.canCodes();
    }

    /** 在当前 App 上有没有这个权限 */
    public boolean can(String perm) {
        if ("codes".equals(perm)) return codes();
        CurrentAdmin me = access.current();
        return me != null && me.can(AppContext.get(), AppPerm.of(perm));
    }

    /** 看过的引导，逗号分隔，页面脚本据此决定要不要自动弹出 */
    public String guide() {
        CurrentAdmin me = access.current();
        return me == null ? "" : me.guide();
    }

    /** 在指定 App 上有没有这个权限 */
    public boolean canOn(Long appId, String perm) {
        CurrentAdmin me = access.current();
        return me != null && me.can(appId, AppPerm.of(perm));
    }
}
