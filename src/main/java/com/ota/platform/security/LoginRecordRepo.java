package com.ota.platform.security;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

public interface LoginRecordRepo extends JpaRepository<LoginRecord, Long> {

    List<LoginRecord> findTop10ByAdminIdOrderByAtDesc(Long adminId);

    List<LoginRecord> findTop8ByOrderByAtDesc();

    @Modifying
    @Transactional
    @Query("delete from LoginRecord r where r.at < ?1")
    int deleteOlderThan(Instant before);

    @Modifying
    @Transactional
    @Query("delete from LoginRecord r where r.adminId = ?1")
    int deleteByAdmin(Long adminId);

    @Modifying
    @Transactional
    @Query("delete from LoginRecord r where r.adminId = ?1 and r.at < ?2")
    int deleteByAdminBefore(Long adminId, Instant before);
}
