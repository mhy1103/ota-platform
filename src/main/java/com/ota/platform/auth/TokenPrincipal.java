package com.ota.platform.auth;

import com.ota.platform.common.ApiException;

/** 通过令牌认证的调用方。 */
public record TokenPrincipal(Long tokenId, String name, String scope, Long appId) {

    public boolean isAdmin() {
        return ApiToken.SCOPE_ADMIN.equals(scope);
    }

    /** 管理令牌可以操作任何 App；发布令牌只能操作自己的 App */
    public void requireApp(Long targetAppId) {
        if (!isAdmin() && !targetAppId.equals(appId)) {
            throw ApiException.forbidden("这个令牌不能操作该 App");
        }
    }

    public void requireAdmin() {
        if (!isAdmin()) throw ApiException.forbidden("需要管理令牌");
    }
}
