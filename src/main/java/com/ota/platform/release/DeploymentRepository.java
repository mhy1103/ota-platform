package com.ota.platform.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {

    Optional<Deployment> findByChannelIdAndRuntimeVersion(Long channelId, String runtimeVersion);

    List<Deployment> findByChannelIdOrderByRuntimeVersionAsc(Long channelId);

    boolean existsByReleaseIdOrPreviousReleaseId(String releaseId, String previousReleaseId);

    List<Deployment> findByReleaseIdOrPreviousReleaseId(String releaseId, String previousReleaseId);

    List<Deployment> findByActivationReleaseId(String activationReleaseId);

    long countByChannelId(Long channelId);

    List<Deployment> findByChannelIdIn(java.util.Collection<Long> channelIds);

    @Modifying
    @Query("delete from Deployment d where d.channelId = :channelId")
    int deleteByChannelId(@Param("channelId") Long channelId);
}
