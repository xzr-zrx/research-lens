package org.example.repository;

import org.example.entity.PaperAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaperAnalysisRepository extends JpaRepository<PaperAnalysis, Long> {
    Optional<PaperAnalysis> findByProjectPaperId(Long projectPaperId);
    List<PaperAnalysis> findByProjectPaperIdIn(List<Long> projectPaperIds);
    @org.springframework.transaction.annotation.Transactional
    void deleteByProjectPaperId(Long projectPaperId);
}
