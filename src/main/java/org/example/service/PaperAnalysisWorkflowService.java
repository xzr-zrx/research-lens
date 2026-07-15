package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ResearchProfilePayload;
import org.example.entity.*;
import org.example.enums.*;
import org.example.repository.*;
import org.example.util.EvidenceValidator;
import org.example.util.JsonUtils;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;

@Service
public class PaperAnalysisWorkflowService {
    private final ResearchProjectService projectService;
    private final ResearchProfileService profileService;
    private final AnalysisTaskService taskService;
    private final ProjectPaperRepository projectPaperRepository;
    private final PaperRepository paperRepository;
    private final PaperAnalysisRepository analysisRepository;
    private final EvidenceRepository evidenceRepository;
    private final ChatService chatService;
    private final DocumentParserService parserService;
    private final ResearchReportService reportService;
    private final ObjectMapper objectMapper;

    public PaperAnalysisWorkflowService(ResearchProjectService projectService,
                                        ResearchProfileService profileService,
                                        AnalysisTaskService taskService,
                                        ProjectPaperRepository projectPaperRepository,
                                        PaperRepository paperRepository,
                                        PaperAnalysisRepository analysisRepository,
                                        EvidenceRepository evidenceRepository,
                                        ChatService chatService,
                                        DocumentParserService parserService,
                                        ResearchReportService reportService,
                                        ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.profileService = profileService;
        this.taskService = taskService;
        this.projectPaperRepository = projectPaperRepository;
        this.paperRepository = paperRepository;
        this.analysisRepository = analysisRepository;
        this.evidenceRepository = evidenceRepository;
        this.chatService = chatService;
        this.parserService = parserService;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void run(Long taskId, Long projectId) {
        try {
            ResearchProfilePayload profile = profileService.get(projectId);
            List<ProjectPaper> selected = projectPaperRepository.findByProjectIdAndSelectedTrueOrderByRankNumberAsc(projectId)
                    .stream().limit(10).toList();
            if (selected.isEmpty()) throw new IllegalStateException("请至少选择一篇论文再分析");
            projectService.updateStatus(projectId, ProjectStatus.ANALYZING);
            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.ANALYZING_PAPERS, 5, "开始逐篇分析论文");

            for (int i = 0; i < selected.size(); i++) {
                ProjectPaper projectPaper = selected.get(i);
                Paper paper = paperRepository.findById(projectPaper.getPaperId())
                        .orElseThrow(() -> new IllegalStateException("论文数据不存在: " + projectPaper.getPaperId()));
                try {
                    analyzeOne(profile, projectPaper, paper);
                } catch (Exception e) {
                    saveFailure(projectPaper, e.getMessage());
                }
                int progress = 5 + (int) ((i + 1) * 80.0 / selected.size());
                taskService.update(taskId, TaskStatus.RUNNING, TaskStage.ANALYZING_PAPERS, progress,
                        "已分析 " + (i + 1) + "/" + selected.size() + " 篇论文");
            }

            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.GENERATING_REPORT, 90, "正在生成研究对比报告");
            reportService.generate(projectId);
            projectService.updateStatus(projectId, ProjectStatus.COMPLETED);
            taskService.update(taskId, TaskStatus.COMPLETED, TaskStage.COMPLETED, 100, "论文分析与报告生成完成");
        } catch (Exception e) {
            projectService.updateStatus(projectId, ProjectStatus.FAILED);
            taskService.fail(taskId, e);
        }
    }

    private void analyzeOne(ResearchProfilePayload profile, ProjectPaper projectPaper, Paper paper) throws Exception {
        EvidenceLevel level = EvidenceLevel.ABSTRACT;
        String sourceText = paper.getAbstractText() == null ? "" : paper.getAbstractText();
        if (paper.getLocalFullTextPath() != null && !paper.getLocalFullTextPath().isBlank()) {
            try {
                String parsed = parserService.parse(Path.of(paper.getLocalFullTextPath()));
                if (parsed != null && !parsed.isBlank()) {
                    sourceText = parsed;
                    level = EvidenceLevel.FULL_TEXT;
                    projectPaper.setEvidenceLevel(level);
                    projectPaperRepository.save(projectPaper);
                }
            } catch (Exception ignored) {
            }
        }
        if (sourceText.isBlank()) throw new IllegalStateException("论文没有摘要，也未上传全文");

        PaperAnalysis analysis;
        if (chatService.isConfigured()) {
            try {
                analysis = analyzeByLlm(profile, projectPaper, paper, sourceText, level);
            } catch (Exception e) {
                analysis = fallbackAnalysis(projectPaper, paper, sourceText, level);
            }
        } else {
            analysis = fallbackAnalysis(projectPaper, paper, sourceText, level);
        }
        analysisRepository.save(analysis);
    }

    private PaperAnalysis analyzeByLlm(ResearchProfilePayload profile,
                                       ProjectPaper projectPaper,
                                       Paper paper,
                                       String sourceText,
                                       EvidenceLevel level) throws Exception {
        String prompt = """
                请比较用户研究方案与下面论文。严格返回 JSON 对象，不要 Markdown。
                字段：researchProblem, method, researchObject, inputOrExperiment, mainResults,
                limitations, relationToUserIdea, relationType, confidence, evidence。
                relationType 只能是 HIGHLY_RELATED、SAME_PROBLEM_DIFFERENT_METHOD、SIMILAR_METHOD_DIFFERENT_FIELD、INDIRECT_REFERENCE、LOW_RELEVANCE。
                confidence 为 0 到 1。evidence 是原文直接摘录字符串数组，不能改写。
                当前证据级别为 %s；如果是 ABSTRACT，只能依据标题和摘要，不能声称阅读全文。

                用户研究问题：%s
                用户方案：%s
                用户关键词：%s

                论文标题：%s
                论文内容：
                %s
                """.formatted(level, profile.researchProblem(), profile.proposedMethod(), profile.keywordsEn(),
                paper.getTitle(), limit(sourceText, 18000));
        String raw = chatService.ask("你是严谨的科研文献比较助手，只能依据提供的论文文本作答。", prompt);
        JsonNode node = objectMapper.readTree(JsonUtils.extractJsonObject(raw));
        PaperAnalysis analysis = baseAnalysis(projectPaper, level);
        analysis.setResearchProblem(JsonUtils.text(node, "researchProblem"));
        analysis.setMethod(JsonUtils.text(node, "method"));
        analysis.setResearchObject(JsonUtils.text(node, "researchObject"));
        analysis.setInputOrExperiment(JsonUtils.text(node, "inputOrExperiment"));
        analysis.setMainResults(JsonUtils.text(node, "mainResults"));
        analysis.setLimitations(JsonUtils.text(node, "limitations"));
        analysis.setRelationToUserIdea(JsonUtils.text(node, "relationToUserIdea"));
        analysis.setRelationType(parseRelation(JsonUtils.text(node, "relationType"), projectPaper.getFinalScore()));
        analysis.setConfidence(node.path("confidence").asDouble(Math.max(0.2, projectPaper.getFinalScore())));
        analysis.setAnalysisJson(objectMapper.writeValueAsString(node));

        evidenceRepository.deleteByProjectPaperId(projectPaper.getId());
        for (String quote : JsonUtils.stringArray(node, "evidence")) {
            if (!EvidenceValidator.isDirectSubstring(quote, sourceText)) continue;
            Evidence evidence = new Evidence();
            evidence.setProjectPaperId(projectPaper.getId());
            evidence.setSection(level == EvidenceLevel.ABSTRACT ? "Abstract" : "Full text");
            evidence.setContent(quote.trim());
            evidence.setEvidenceLevel(level);
            evidenceRepository.save(evidence);
        }
        return analysis;
    }

    private PaperAnalysis fallbackAnalysis(ProjectPaper projectPaper, Paper paper, String sourceText, EvidenceLevel level) {
        PaperAnalysis analysis = baseAnalysis(projectPaper, level);
        analysis.setResearchProblem("根据论文标题与可用文本判断，该工作围绕“" + paper.getTitle() + "”展开。");
        analysis.setMethod(firstSentence(sourceText));
        analysis.setResearchObject(paper.getVenue() == null || paper.getVenue().isBlank() ? "未从当前证据中明确提取" : paper.getVenue());
        analysis.setInputOrExperiment("当前自动分析未可靠提取实验配置，建议阅读全文确认。");
        analysis.setMainResults(secondSentence(sourceText));
        analysis.setLimitations("当前结论主要基于可用摘要或上传全文的文本检索，细节需人工核对。");
        analysis.setRelationToUserIdea("论文相关性排序得分为 " + String.format("%.3f", projectPaper.getFinalScore()) + "，可作为进一步阅读线索。");
        analysis.setRelationType(parseRelation("", projectPaper.getFinalScore()));
        analysis.setConfidence(Math.max(0.2, Math.min(0.75, projectPaper.getFinalScore())));
        try {
            analysis.setAnalysisJson(objectMapper.writeValueAsString(java.util.Map.of("fallback", true)));
        } catch (Exception ignored) {}
        evidenceRepository.deleteByProjectPaperId(projectPaper.getId());
        String quote = sourceText.length() > 320 ? sourceText.substring(0, 320) : sourceText;
        if (!quote.isBlank()) {
            Evidence evidence = new Evidence();
            evidence.setProjectPaperId(projectPaper.getId());
            evidence.setSection(level == EvidenceLevel.ABSTRACT ? "Abstract" : "Full text");
            evidence.setContent(quote.trim());
            evidence.setEvidenceLevel(level);
            evidenceRepository.save(evidence);
        }
        return analysis;
    }

    private PaperAnalysis baseAnalysis(ProjectPaper projectPaper, EvidenceLevel level) {
        PaperAnalysis analysis = analysisRepository.findByProjectPaperId(projectPaper.getId()).orElseGet(PaperAnalysis::new);
        analysis.setProjectPaperId(projectPaper.getId());
        analysis.setEvidenceLevel(level);
        analysis.setFailureReason(null);
        return analysis;
    }

    private void saveFailure(ProjectPaper projectPaper, String message) {
        PaperAnalysis analysis = analysisRepository.findByProjectPaperId(projectPaper.getId()).orElseGet(PaperAnalysis::new);
        analysis.setProjectPaperId(projectPaper.getId());
        analysis.setEvidenceLevel(projectPaper.getEvidenceLevel());
        analysis.setRelationType(RelationType.LOW_RELEVANCE);
        analysis.setConfidence(0.0);
        analysis.setFailureReason(message);
        analysisRepository.save(analysis);
    }

    private RelationType parseRelation(String value, Double score) {
        try { return RelationType.valueOf(value); } catch (Exception ignored) {}
        double s = score == null ? 0 : score;
        if (s >= 0.72) return RelationType.HIGHLY_RELATED;
        if (s >= 0.55) return RelationType.SAME_PROBLEM_DIFFERENT_METHOD;
        if (s >= 0.40) return RelationType.SIMILAR_METHOD_DIFFERENT_FIELD;
        if (s >= 0.25) return RelationType.INDIRECT_REFERENCE;
        return RelationType.LOW_RELEVANCE;
    }

    private String firstSentence(String text) {
        String[] values = text.split("(?<=[.!?。！？])\\s+");
        return values.length == 0 ? text : values[0];
    }
    private String secondSentence(String text) {
        String[] values = text.split("(?<=[.!?。！？])\\s+");
        return values.length > 1 ? values[1] : firstSentence(text);
    }
    private String limit(String text, int max) { return text.length() <= max ? text : text.substring(0, max); }
}
