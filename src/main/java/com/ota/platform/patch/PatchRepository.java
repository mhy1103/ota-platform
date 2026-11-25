package com.ota.platform.patch;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PatchRepository extends JpaRepository<Patch, Long> {

    Optional<Patch> findByBaseShaAndTargetSha(String baseSha, String targetSha);

    List<Patch> findByTargetShaIn(Collection<String> targetShas);

    /** 基础文件或目标文件已经没有版本在用的补丁 */
    @Query("select p from Patch p where p.targetSha not in (select f.assetSha256 from ReleaseFile f)"
            + " or p.baseSha not in (select f.assetSha256 from ReleaseFile f)")
    List<Patch> findOrphans();

    /** [补丁数, 字节数] */
    @Query("select count(p), coalesce(sum(p.patchSize), 0) from Patch p where p.status = 'READY'")
    List<Object[]> readyUsage();
}
