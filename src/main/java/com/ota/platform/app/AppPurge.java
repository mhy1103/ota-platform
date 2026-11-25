package com.ota.platform.app;

/** 删除 App 时要一起清掉的数据：新加的按 App 存的数据实现这个接口，删除 App 时自动调用 */
public interface AppPurge {
    void purge(Long appId);
}
