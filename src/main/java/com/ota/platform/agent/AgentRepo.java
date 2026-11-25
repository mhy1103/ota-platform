package com.ota.platform.agent;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepo extends JpaRepository<Agent, Long> {
    Optional<Agent> findByUsername(String username);
    boolean existsByUsername(String username);
    List<Agent> findAllByOrderByCreatedAtDesc();
    /** 一级代理（管理员直接管的） */
    List<Agent> findByParentIdIsNullOrderByCreatedAtDesc();
    List<Agent> findByParentIdOrderByCreatedAtDesc(Long parentId);
    List<Agent> findByParentIdIsNotNull();
    long countByParentId(Long parentId);
}
