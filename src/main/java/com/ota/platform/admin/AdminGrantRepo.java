package com.ota.platform.admin;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AdminGrantRepo extends JpaRepository<AdminGrant, Long> {
    List<AdminGrant> findByAdminId(Long adminId);
    List<AdminGrant> findByAppId(Long appId);

    @Modifying
    @Query("delete from AdminGrant g where g.adminId = :adminId")
    int deleteByAdminId(@Param("adminId") Long adminId);

    @Modifying
    @Query("delete from AdminGrant g where g.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);
}
