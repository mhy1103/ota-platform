package com.ota.platform.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CodeRequestItemRepo extends JpaRepository<CodeRequestItem, Long> {

    List<CodeRequestItem> findByRequestIdOrderByIdAsc(Long requestId);

    List<CodeRequestItem> findByRequestIdInOrderByIdAsc(Collection<Long> requestIds);

    /** 待审核的申请里已经有人要了的口令 */
    @Query("select i.code from CodeRequestItem i where i.status = 'PENDING' and i.code in :codes")
    List<String> pendingCodes(@Param("codes") Collection<String> codes);

    @Modifying
    @Query("delete from CodeRequestItem i where i.requestId in (select r.id from CodeRequest r where r.agentId = :agentId)")
    int deleteByAgentId(@Param("agentId") Long agentId);
}
