package com.ota.platform.share;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ScreenShareRepo extends JpaRepository<ScreenShare, Long> {

    Optional<ScreenShare> findByToken(String token);

    List<ScreenShare> findTop200ByOrderByCreatedAtDesc();

    List<ScreenShare> findTop200ByCreatedByIgnoreCaseOrderByCreatedAtDesc(String createdBy);

    /** 这个人还有效（没取消、没过期）的分享 */
    @Query("select count(s) from ScreenShare s where lower(s.createdBy) = lower(:u) and s.revokedAt is null and (s.expiresAt is null or s.expiresAt > :now)")
    long countActive(@Param("u") String username, @Param("now") LocalDateTime now);
}
