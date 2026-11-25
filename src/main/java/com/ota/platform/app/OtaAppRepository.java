package com.ota.platform.app;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface OtaAppRepository extends JpaRepository<OtaApp, Long> {

    Optional<OtaApp> findBySlug(String slug);

    Optional<OtaApp> findByCodeKey(String codeKey);

    boolean existsBySlug(String slug);

    List<OtaApp> findAllByOrderByCreatedAtAsc();
}
