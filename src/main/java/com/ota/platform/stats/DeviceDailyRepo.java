package com.ota.platform.stats;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DeviceDailyRepo extends JpaRepository<DeviceDaily, Long> {

    Optional<DeviceDaily> findByAppIdAndDay(Long appId, LocalDate day);

    List<DeviceDaily> findByAppIdAndDayGreaterThanEqualOrderByDayAsc(Long appId, LocalDate from);

    Optional<DeviceDaily> findFirstByAppIdOrderByDayAsc(Long appId);

    /** 数据大屏：这些 App 从 from 那天起每天的设备数 */
    List<DeviceDaily> findByAppIdInAndDayGreaterThanEqual(java.util.Collection<Long> appIds, LocalDate from);

    @Modifying
    @Transactional
    @Query("delete from DeviceDaily d where d.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);

    @Modifying
    @Transactional
    @Query("delete from DeviceDaily d where d.day < :before")
    int deleteOlderThan(@Param("before") LocalDate before);
}
