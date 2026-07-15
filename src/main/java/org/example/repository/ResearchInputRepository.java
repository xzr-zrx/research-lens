package org.example.repository;

import org.example.entity.ResearchInput;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ResearchInputRepository extends JpaRepository<ResearchInput, Long> {
    List<ResearchInput> findByProjectIdOrderByCreatedAtAsc(Long projectId);
    @Transactional
    void deleteByProjectId(Long projectId);
}
