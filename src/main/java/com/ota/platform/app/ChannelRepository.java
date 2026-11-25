package com.ota.platform.app;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {

    Optional<Channel> findByAppIdAndName(Long appId, String name);

    List<Channel> findByAppIdOrderByCreatedAtAsc(Long appId);

    List<Channel> findByAppIdIn(java.util.Collection<Long> appIds);
}
