package org.example.service;

import org.example.entity.AnalysisTask;
import org.example.enums.TaskStage;
import org.example.enums.TaskStatus;
import org.example.enums.TaskType;
import org.example.exception.NotFoundException;
import org.example.repository.AnalysisTaskRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AnalysisTaskService {
    private final AnalysisTaskRepository repository;

    public AnalysisTaskService(AnalysisTaskRepository repository) {
        this.repository = repository;
    }

    public AnalysisTask create(Long projectId, TaskType type) {
        AnalysisTask task = new AnalysisTask();
        task.setProjectId(projectId);
        task.setTaskType(type);
        task.setStatus(TaskStatus.PENDING);
        task.setStage(TaskStage.CREATED);
        task.setProgress(0);
        task.setMessage("任务已创建");
        return repository.save(task);
    }

    public AnalysisTask get(Long id) {
        return repository.findById(id).orElseThrow(() -> new NotFoundException("任务不存在: " + id));
    }

    public List<AnalysisTask> listByProject(Long projectId) {
        return repository.findByProjectIdOrderByCreatedAtDesc(projectId);
    }

    public AnalysisTask update(Long id, TaskStatus status, TaskStage stage, int progress, String message) {
        AnalysisTask task = get(id);
        task.setStatus(status);
        task.setStage(stage);
        task.setProgress(Math.max(0, Math.min(100, progress)));
        task.setMessage(message);
        if (status == TaskStatus.COMPLETED || status == TaskStatus.FAILED || status == TaskStatus.INTERRUPTED) {
            task.setFinishedAt(LocalDateTime.now());
        }
        return repository.save(task);
    }

    public void fail(Long id, Throwable error) {
        AnalysisTask task = get(id);
        task.setStatus(TaskStatus.FAILED);
        task.setStage(TaskStage.FAILED);
        task.setProgress(100);
        task.setMessage("任务失败");
        task.setErrorMessage(error == null ? "未知错误" : error.getMessage());
        task.setFinishedAt(LocalDateTime.now());
        repository.save(task);
    }
}
