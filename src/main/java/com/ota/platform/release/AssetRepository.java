package com.ota.platform.release;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface AssetRepository extends JpaRepository<Asset, String> {

    /** 没有任何版本、补丁引用的文件（before 之前登记的，刚上传还没登记版本的不算） */
    @Query("select a from Asset a where a.createdAt < :before"
            + " and a.sha256 not in (select f.assetSha256 from ReleaseFile f)"
            + " and a.sha256 not in (select p.patchSha from Patch p where p.patchSha is not null)")
    List<Asset> findUnreferenced(@Param("before") Instant before);

    /** [文件数, 字节数]：存在本平台存储里的版本文件（外部链接版本的文件不在这里） */
    @Query("select count(a), coalesce(sum(a.size), 0) from Asset a where a.sha256 in"
            + " (select f.assetSha256 from ReleaseFile f where f.releaseId in (select r.id from Release r where r.externalBaseUrl is null))")
    List<Object[]> storedUsage();
}
