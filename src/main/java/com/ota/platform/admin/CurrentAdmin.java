package com.ota.platform.admin;

import java.util.Map;
import java.util.Set;

/** 这次请求的管理员和他的授权（每次请求从数据库读，改了权限立即生效） */
public record CurrentAdmin(Long id, String username, String name, boolean owner, boolean manageAgents, boolean manageCodes,
                           Map<Long, Set<AppPerm>> grants, String guide) {

    public boolean canSee(Long appId) {
        return owner || (appId != null && grants.containsKey(appId));
    }

    public boolean can(Long appId, AppPerm perm) {
        return owner || (appId != null && perm != null && grants.getOrDefault(appId, Set.of()).contains(perm));
    }

    public boolean canManageAgents() {
        return owner || manageAgents;
    }

    /** 口令与激活：新建、编辑、删除、导入口令，导出激活记录，审核代理的口令申请 */
    public boolean canCodes() {
        return owner || manageCodes;
    }

    public String roleLabel() {
        return owner ? "超级管理员" : "成员";
    }
}
