package org.example.config;

import org.example.entity.ProjectPaper;
import org.example.enums.EvidenceLevel;
import org.example.enums.KnowledgeStatus;
import org.example.repository.ProjectPaperRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 兼容旧版本数据。旧版本只有 savedToKnowledge，无法可靠证明当时索引的是全文，
 * 因此一律迁移为 ABSTRACT_ONLY，避免把摘要错误标记成全文证据。
 */
@Component
public class KnowledgeStatusMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeStatusMigrationRunner.class);
    private final ProjectPaperRepository repository;

    public KnowledgeStatusMigrationRunner(ProjectPaperRepository repository) { this.repository = repository; }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = 0;
        for (ProjectPaper paper : repository.findAll()) {
            if (paper.getKnowledgeStatus() == null) {
                paper.setKnowledgeStatus(paper.isSavedToKnowledge() ? KnowledgeStatus.ABSTRACT_ONLY : KnowledgeStatus.NOT_INDEXED);
                if (paper.isSavedToKnowledge()) paper.setEvidenceLevel(EvidenceLevel.ABSTRACT);
                paper.setKnowledgeUpdatedAt(LocalDateTime.now());
                repository.save(paper); migrated++;
            } else if (paper.isSavedToKnowledge() && paper.getKnowledgeStatus() == KnowledgeStatus.NOT_INDEXED) {
                paper.setKnowledgeStatus(KnowledgeStatus.ABSTRACT_ONLY);
                paper.setEvidenceLevel(EvidenceLevel.ABSTRACT);
                paper.setKnowledgeUpdatedAt(LocalDateTime.now());
                repository.save(paper); migrated++;
            }
        }
        if (migrated > 0) log.info("已迁移 {} 条旧版论文知识库状态为安全的摘要级证据", migrated);
    }
}
