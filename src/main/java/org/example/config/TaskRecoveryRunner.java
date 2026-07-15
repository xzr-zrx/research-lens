package org.example.config;

import org.example.entity.AnalysisTask;
import org.example.enums.TaskStage;
import org.example.enums.TaskStatus;
import org.example.repository.AnalysisTaskRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TaskRecoveryRunner implements ApplicationRunner {
    private final AnalysisTaskRepository repository;

    public TaskRecoveryRunner(AnalysisTaskRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<AnalysisTask> tasks = repository.findByStatusIn(List.of(TaskStatus.PENDING, TaskStatus.RUNNING));
        for (AnalysisTask task : tasks) {
            task.setStatus(TaskStatus.INTERRUPTED);
            task.setStage(TaskStage.INTERRUPTED);
            task.setProgress(100);
            task.setMessage("应用重启，未完成任务已中断，请重新执行");
            task.setFinishedAt(LocalDateTime.now());
        }
        repository.saveAll(tasks);
    }
}
