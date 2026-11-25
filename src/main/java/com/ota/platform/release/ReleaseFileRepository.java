package com.ota.platform.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReleaseFileRepository extends JpaRepository<ReleaseFile, Long> {

    List<ReleaseFile> findByReleaseIdAndPlatformOrderBySortOrderAsc(String releaseId, String platform);

    List<ReleaseFile> findByReleaseIdOrderBySortOrderAsc(String releaseId);

    Optional<ReleaseFile> findFirstByReleaseIdAndPlatformAndLaunchTrue(String releaseId, String platform);

    /** 用到这些文件的版本（外部链接版本的文件不在本平台存储里，不算） */
    @Query("select distinct r.id from ReleaseFile f, Release r where f.assetSha256 in :shas and r.id = f.releaseId"
            + " and r.externalBaseUrl is null")
    List<String> storedReleasesUsing(@Param("shas") Collection<String> shas);

    @Modifying
    @Query("delete from ReleaseFile f where f.releaseId = :releaseId")
    int deleteByReleaseId(@Param("releaseId") String releaseId);
}
