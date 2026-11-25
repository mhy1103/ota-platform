package com.ota.platform.installer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InstallerRepository extends JpaRepository<InstallerPackage, String> {

    List<InstallerPackage> findByAppIdOrderByUploadedAtDesc(Long appId);

    List<InstallerPackage> findByAppIdAndRuntimeVersion(Long appId, String runtimeVersion);

    boolean existsBySha256AndIdNot(String sha256, String id);

    void deleteByAppId(Long appId);
}
