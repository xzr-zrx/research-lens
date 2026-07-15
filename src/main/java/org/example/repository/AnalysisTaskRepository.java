package org.example.repository;

import org.example.entity.AnalysisTask;
import org.example.enums.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AnalysisTaskRepository extends JpaRepository<AnalysisTask, Long> {
    List<AnalysisTask> findByStatusIn(Collection<TaskStatus> statuses);
    List<AnalysisTask> findByProjectIdOrderByCreatedAtDesc(Long projectId);
    @org.springframework.transaction.annotation.Transactional
    void deleteByProjectId(Long projectId);
}
