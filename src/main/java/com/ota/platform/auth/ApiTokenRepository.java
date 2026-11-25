package com.ota.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApiTokenRepository extends JpaRepository<ApiToken, Long> {

    Optional<ApiToken> findByTokenHash(String tokenHash);

    long countByScope(String scope);

    List<ApiToken> findByAppIdOrderByCreatedAtDesc(Long appId);

    List<ApiToken> findByScopeOrderByCreatedAtDesc(String scope);

    @Modifying
    @Query("delete from ApiToken t where t.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);
}
