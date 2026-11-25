package com.ota.platform.notice;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface NoticeReadRepo extends JpaRepository<NoticeRead, Long> {

    List<NoticeRead> findByAgentId(Long agentId);

    /** [公告 ID, 这些代理里看过的人数] */
    @Query("select r.noticeId, count(r) from NoticeRead r where r.agentId in :agentIds group by r.noticeId")
    List<Object[]> countByNotice(@Param("agentIds") Collection<Long> agentIds);

    @Modifying
    @Transactional
    @Query("delete from NoticeRead r where r.noticeId = :noticeId")
    int deleteByNotice(@Param("noticeId") Long noticeId);

    @Modifying
    @Transactional
    @Query("delete from NoticeRead r where r.agentId in :agentIds")
    int deleteByAgents(@Param("agentIds") Collection<Long> agentIds);
}
