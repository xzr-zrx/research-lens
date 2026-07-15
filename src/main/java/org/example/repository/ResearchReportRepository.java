package org.example.repository;

import org.example.entity.ResearchReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ResearchReportRepository extends JpaRepository<ResearchReport, Long> {
    Optional<ResearchReport> findFirstByProjectIdOrderByVersionDesc(Long projectId);
    List<ResearchReport> findByProjectIdOrderByVersionDesc(Long projectId);
    @org.springframework.transaction.annotation.Transactional
    void deleteByProjectId(Long projectId);
}
