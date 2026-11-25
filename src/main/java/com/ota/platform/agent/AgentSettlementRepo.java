package com.ota.platform.agent;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface AgentSettlementRepo extends JpaRepository<AgentSettlement, Long> {

    List<AgentSettlement> findByAgentIdIn(Collection<Long> agentIds);

    /** 每个代理结算到的时刻：[agentId, settledTo] */
    @Query("select s.agentId, max(s.settledTo) from AgentSettlement s group by s.agentId")
    List<Object[]> settledTo();

    /** 结算时扣掉过激活的代理 */
    @Query("select distinct s.agentId from AgentSettlement s where s.shownCount < s.realCount")
    List<Long> deductedAgents();

    @Modifying
    @Transactional
    @Query("delete from AgentSettlement s where s.agentId in :ids")
    int deleteByAgentIds(@Param("ids") Collection<Long> ids);
}
