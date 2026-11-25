package com.ota.platform.ota;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TestDeviceRepo extends JpaRepository<TestDevice, Long> {
    List<TestDevice> findByAppIdOrderByCreatedAtDesc(Long appId);
    Optional<TestDevice> findByAppIdAndClientId(Long appId, String clientId);
    long countByAppId(Long appId);

    @Modifying
    @Query("delete from TestDevice t where t.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);

    @Modifying
    @Query("delete from TestDevice t where t.appId = :appId and t.channel = :channel")
    int deleteByAppIdAndChannel(@Param("appId") Long appId, @Param("channel") String channel);
}
