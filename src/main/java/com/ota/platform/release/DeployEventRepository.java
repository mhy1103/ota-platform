package com.ota.platform.release;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DeployEventRepository extends JpaRepository<DeployEvent, Long> {

    @Query("select e from DeployEvent e where e.appId = ?1 order by e.at desc, e.id desc")
    List<DeployEvent> recent(Long appId, Pageable page);

    /** 数据大屏：这些 App 最近的发布记录 */
    @Query("select e from DeployEvent e where e.appId in ?1 order by e.at desc, e.id desc")
    List<DeployEvent> recentIn(java.util.Collection<Long> appIds, Pageable page);

    @Modifying
    @Transactional
    @Query("delete from DeployEvent e where e.appId = ?1")
    int deleteByAppId(Long appId);
}
