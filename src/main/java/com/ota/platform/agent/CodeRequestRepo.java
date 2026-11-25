package com.ota.platform.agent;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface CodeRequestRepo extends JpaRepository<CodeRequest, Long> {

    List<CodeRequest> findByAgentIdOrderByCreatedAtDesc(Long agentId, Pageable page);

    List<CodeRequest> findByStatusOrderByCreatedAtAsc(String status);

    List<CodeRequest> findByStatusOrderByCreatedAtDesc(String status, Pageable page);

    List<CodeRequest> findAllByOrderByCreatedAtDesc(Pageable page);

    long countByStatus(String status);

    long countByAgentIdAndStatus(Long agentId, String status);

    /** 某个时间以后申请的口令个数：待审核的按申请数，已通过的按实际生成数 */
    @Query("select coalesce(sum(case when r.status = 'APPROVED' then coalesce(r.codeCount, r.count) else r.count end), 0) "
            + "from CodeRequest r where r.agentId = :agentId and r.createdAt >= :since and r.status in ('PENDING', 'APPROVED')")
    long requestedSince(@Param("agentId") Long agentId, @Param("since") LocalDateTime since);

    @Modifying
    @Query("delete from CodeRequest r where r.agentId = :agentId")
    int deleteByAgentId(@Param("agentId") Long agentId);
}
