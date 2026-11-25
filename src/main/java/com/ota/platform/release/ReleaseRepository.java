package com.ota.platform.release;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ReleaseRepository extends JpaRepository<Release, String> {

    List<Release> findByAppIdOrderByCreatedAtDesc(Long appId, Pageable pageable);

    List<Release> findByAppIdOrderByCreatedAtDesc(Long appId);

    List<Release> findByAppIdAndRuntimeVersionAndCreatedAtBeforeOrderByCreatedAtDesc(Long appId, String runtimeVersion,
                                                                                   Instant before, Pageable pageable);

    long countByAppId(Long appId);

    /** 只要版本 ID（版本内容很大，统计时不用取出来） */
    @org.springframework.data.jpa.repository.Query("select r.id from Release r where r.appId = ?1")
    List<String> idsOf(Long appId);

    long countByAppIdAndCreatedAtAfter(Long appId, java.time.Instant after);

    /** 文件存在本平台存储里的版本数 */
    long countByExternalBaseUrlIsNull();

    /** 外部链接版本数：文件不在本平台存储里，换存储不影响 */
    long countByExternalBaseUrlIsNotNull();
}
