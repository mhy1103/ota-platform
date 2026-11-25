package com.ota.platform.codes;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ActivationCodeRepo extends JpaRepository<ActivationCode, Long>, JpaSpecificationExecutor<ActivationCode> {
    Optional<ActivationCode> findByCode(String code);
    boolean existsByCode(String code);
    List<ActivationCode> findAllByOrderByCreatedAtDesc();
    List<ActivationCode> findByAgentIdOrderByCreatedAtDesc(Long agentId);
    List<ActivationCode> findBySubAgentIdOrderByCreatedAtDesc(Long subAgentId);

    /** [口令 ID, 代理 ID]：这些口令各属于哪个代理（未分配的是 null） */
    @Query("select c.id, c.agentId from ActivationCode c where c.id in :ids")
    List<Object[]> agentsOf(@Param("ids") java.util.Collection<Long> ids);

    /** 用量在数据库里加一，并发确认不会互相覆盖 */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ActivationCode c set c.usedCount = coalesce(c.usedCount, 0) + 1 where c.id = :id")
    int incrementUsed(@Param("id") Long id);

    /** 代理看到的次数（见 ActivationCode.seenCount），null 是跟实际的一样 */
    @Modifying
    @Query("update ActivationCode c set c.seenCount = :seen where c.id = :id")
    int updateSeen(@Param("id") Long id, @Param("seen") Integer seen);

    /** 不归哪个代理了的口令，代理看到的次数清掉 */
    @Modifying
    @Query("update ActivationCode c set c.seenCount = null where c.agentId is null and c.seenCount is not null")
    int clearUnowned();
}
