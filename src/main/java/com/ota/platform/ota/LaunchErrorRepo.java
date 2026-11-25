package com.ota.platform.ota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface LaunchErrorRepo extends JpaRepository<LaunchError, Long> {
    List<LaunchError> findTop30ByAppIdOrderByCreatedAtDesc(Long appId);
    List<LaunchError> findTop10ByReleaseIdOrderByCreatedAtDesc(String releaseId);
    List<LaunchError> findTop2000ByReleaseIdOrderByCreatedAtDesc(String releaseId);
    long countByAppIdAndCreatedAtAfter(Long appId, Instant after);
    long countByAppId(Long appId);

    /** 数据大屏：[App ID, 数量]，after 之后这些 App 各有多少条 */
    @Query("select e.appId, count(e) from LaunchError e where e.appId in :ids and e.createdAt > :after group by e.appId")
    List<Object[]> countByAppSince(@Param("ids") java.util.Collection<Long> ids, @Param("after") Instant after);

    @Modifying
    @Query("delete from LaunchError e where e.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);

    /** 每个 App 只留最近的一批 */
    @Modifying
    @Query("delete from LaunchError e where e.appId = :appId and e.createdAt < :before")
    int deleteOlder(@Param("appId") Long appId, @Param("before") Instant before);
}
