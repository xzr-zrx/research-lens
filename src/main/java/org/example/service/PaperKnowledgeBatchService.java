package org.example.service;

import org.example.entity.ProjectPaper;
import org.example.enums.TaskStage;
import org.example.enums.TaskStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PaperKnowledgeBatchService {
    private final AnalysisTaskService taskService;
    private final PaperKnowledgeService knowledgeService;

    public PaperKnowledgeBatchService(AnalysisTaskService taskService, PaperKnowledgeService knowledgeService) {
        this.taskService = taskService;
        this.knowledgeService = knowledgeService;
    }

    @Async
    public void run(Long taskId, List<Long> projectPaperIds) {
        int total = projectPaperIds == null ? 0 : projectPaperIds.size();
        if (total == 0) { taskService.fail(taskId, new IllegalArgumentException("没有选择需要入库的论文")); return; }
        int success = 0, failed = 0;
        for (int i = 0; i < total; i++) {
            Long id = projectPaperIds.get(i);
            int progress = Math.max(1, (int) (i * 100.0 / total));
            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.FETCHING_FULL_TEXT, progress,
                    "正在处理第 " + (i + 1) + "/" + total + " 篇论文");
            try { ProjectPaper ignored = knowledgeService.saveToKnowledge(id); success++; }
            catch (Exception e) { failed++; }
        }
        taskService.update(taskId, TaskStatus.COMPLETED, TaskStage.COMPLETED, 100,
                "批量入库完成：全文成功 " + success + " 篇，失败/需上传 " + failed + " 篇");
    }
}
