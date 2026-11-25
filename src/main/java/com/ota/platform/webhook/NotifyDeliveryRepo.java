package com.ota.platform.webhook;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface NotifyDeliveryRepo extends JpaRepository<NotifyDelivery, Long> {

    List<NotifyDelivery> findTop30ByAppIdOrderByIdDesc(Long appId);

    List<NotifyDelivery> findByAppIdOrderByIdDesc(Long appId, Pageable pageable);

    long countByAppId(Long appId);

    @Transactional
    @Modifying
    @Query("delete from NotifyDelivery d where d.appId = :appId and d.id < :minId")
    int deleteOlder(@Param("appId") Long appId, @Param("minId") Long minId);

    @Transactional
    long deleteByAppId(Long appId);
}
