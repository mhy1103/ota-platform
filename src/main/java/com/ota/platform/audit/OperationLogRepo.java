package com.ota.platform.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

public interface OperationLogRepo extends JpaRepository<OperationLog, Long>, JpaSpecificationExecutor<OperationLog> {

    @Transactional
    long deleteByActionIn(Collection<String> actions);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("delete from OperationLog l where l.createdAt < :before")
    int deleteOlderThan(@org.springframework.data.repository.query.Param("before") java.time.LocalDateTime before);

    @Transactional
    long deleteByActionNotIn(Collection<String> actions);

    List<OperationLog> findTop8ByActionNotInOrderByCreatedAtDesc(Collection<String> actions);

    List<OperationLog> findTop12ByOperatorOrderByCreatedAtDesc(String operator);

    Page<OperationLog> findByActionIn(Collection<String> actions, Pageable pageable);

    Page<OperationLog> findByActionNotIn(Collection<String> actions, Pageable pageable);

    boolean existsByActionAndDetailStartingWith(String action, String detailPrefix);

    boolean existsByAction(String action);
}
