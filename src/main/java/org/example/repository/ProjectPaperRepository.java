package org.example.repository;

import org.example.entity.ProjectPaper;
import org.example.enums.EvidenceLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface ProjectPaperRepository extends JpaRepository<ProjectPaper, Long> {
    List<ProjectPaper> findByProjectIdOrderByRankNumberAsc(Long projectId);
    List<ProjectPaper> findByProjectIdAndSelectedTrueOrderByRankNumberAsc(Long projectId);
    Optional<ProjectPaper> findByProjectIdAndPaperId(Long projectId, Long paperId);
    List<ProjectPaper> findByProjectIdAndPaperIdIn(Long projectId, List<Long> paperIds);
    long countByEvidenceLevel(EvidenceLevel evidenceLevel);
    long countByEvidenceLevelAndSavedToKnowledgeTrue(EvidenceLevel evidenceLevel);
    @Transactional
    void deleteByProjectId(Long projectId);
}
