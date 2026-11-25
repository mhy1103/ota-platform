package com.ota.platform.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ScheduledTaskRepo extends JpaRepository<ScheduledTask, Long> {

    List<ScheduledTask> findByStatusAndRunAtLessThanEqualOrderByRunAtAsc(String status, LocalDateTime now);

    List<ScheduledTask> findByAppIdAndStatusOrderByRunAtAsc(Long appId, String status);

    List<ScheduledTask> findTop5ByAppIdAndStatusInAndDoneAtGreaterThanEqualOrderByDoneAtDesc(Long appId, java.util.Collection<String> statuses,
                                                                                          LocalDateTime since);

    long countByStatus(String status);

    @Modifying
    @Transactional
    @Query("delete from ScheduledTask t where t.appId = :appId")
    int deleteByAppId(@Param("appId") Long appId);
}
