package com.ota.platform.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceSeenRepository extends JpaRepository<DeviceSeen, Long>, JpaSpecificationExecutor<DeviceSeen> {

    Optional<DeviceSeen> findByAppIdAndClientId(Long appId, String clientId);

    Optional<DeviceSeen> findFirstByAppIdAndClientIdIgnoreCase(Long appId, String clientId);

    /** [当前版本 ID, 设备数] */
    @Query("select d.currentUpdateId, count(d) from DeviceSeen d where d.appId = :appId and d.lastSeen >= :since group by d.currentUpdateId")
    List<Object[]> countByUpdate(@Param("appId") Long appId, @Param("since") Instant since);

    /** [平台, 设备数] */
    @Query("select d.platform, count(d) from DeviceSeen d where d.appId = :appId and d.lastSeen >= :since group by d.platform")
    List<Object[]> countByPlatform(@Param("appId") Long appId, @Param("since") Instant since);

    @Query("select d.channel, count(d) from DeviceSeen d where d.appId = :appId and d.lastSeen >= :since group by d.channel")
    List<Object[]> countByChannel(@Param("appId") Long appId, @Param("since") Instant since);

    @Query("select d.runtimeVersion, count(d) from DeviceSeen d where d.appId = :appId and d.lastSeen >= :since group by d.runtimeVersion")
    List<Object[]> countByRuntime(@Param("appId") Long appId, @Param("since") Instant since);

    // ---------- 数据大屏：一条查询管多个 App ----------

    /** [App ID, 平台, 设备数, 其中第一次出现的]：since 之后检查过更新的设备 */
    @Query("select d.appId, d.platform, count(d), sum(case when d.firstSeen >= :since then 1 else 0 end) from DeviceSeen d "
            + "where d.appId in :ids and d.lastSeen >= :since group by d.appId, d.platform")
    List<Object[]> activeByAppPlatform(@Param("ids") java.util.Collection<Long> ids, @Param("since") Instant since);

    /** [App ID, 平台, 当前版本 ID, 设备数] */
    @Query("select d.appId, d.platform, d.currentUpdateId, count(d) from DeviceSeen d where d.appId in :ids and d.lastSeen >= :since "
            + "group by d.appId, d.platform, d.currentUpdateId")
    List<Object[]> countByAppPlatformUpdate(@Param("ids") java.util.Collection<Long> ids, @Param("since") Instant since);

    @Query("select count(d) from DeviceSeen d where d.appId in :ids and d.lastSeen >= :since")
    long countActiveIn(@Param("ids") java.util.Collection<Long> ids, @Param("since") Instant since);

    long countByAppIdAndLastSeenGreaterThanEqual(Long appId, Instant since);

    long countByAppIdAndFirstSeenGreaterThanEqual(Long appId, Instant since);

    @Modifying
    @Query("delete from DeviceSeen d where d.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);
}
