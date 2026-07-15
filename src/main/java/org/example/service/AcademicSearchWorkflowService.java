package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.academic.ArxivClient;
import org.example.academic.OpenAlexClient;
import org.example.dto.*;
import org.example.entity.*;
import org.example.enums.*;
import org.example.repository.*;
import org.example.util.TitleNormalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AcademicSearchWorkflowService {
    private final ResearchProjectService projectService;
    private final ResearchProfileService profileService;
    private final AcademicQueryPlanningService queryPlanningService;
    private final OpenAlexClient openAlexClient;
    private final ArxivClient arxivClient;
    private final PaperDeduplicationService deduplicationService;
    private final PaperRankingService rankingService;
    private final AnalysisTaskService taskService;
    private final SearchQueryRepository searchQueryRepository;
    private final PaperRepository paperRepository;
    private final ProjectPaperRepository projectPaperRepository;
    private final PaperAnalysisRepository paperAnalysisRepository;
    private final EvidenceRepository evidenceRepository;
    private final VectorIndexService vectorIndexService;
    private final ObjectMapper objectMapper;

    @Value("${academic.search.top-results:15}")
    private int topResults;

    public AcademicSearchWorkflowService(ResearchProjectService projectService,
                                         ResearchProfileService profileService,
                                         AcademicQueryPlanningService queryPlanningService,
                                         OpenAlexClient openAlexClient,
                                         ArxivClient arxivClient,
                                         PaperDeduplicationService deduplicationService,
                                         PaperRankingService rankingService,
                                         AnalysisTaskService taskService,
                                         SearchQueryRepository searchQueryRepository,
                                         PaperRepository paperRepository,
                                         ProjectPaperRepository projectPaperRepository,
                                         PaperAnalysisRepository paperAnalysisRepository,
                                         EvidenceRepository evidenceRepository,
                                         VectorIndexService vectorIndexService,
                                         ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.profileService = profileService;
        this.queryPlanningService = queryPlanningService;
        this.openAlexClient = openAlexClient;
        this.arxivClient = arxivClient;
        this.deduplicationService = deduplicationService;
        this.rankingService = rankingService;
        this.taskService = taskService;
        this.searchQueryRepository = searchQueryRepository;
        this.paperRepository = paperRepository;
        this.projectPaperRepository = projectPaperRepository;
        this.paperAnalysisRepository = paperAnalysisRepository;
        this.evidenceRepository = evidenceRepository;
        this.vectorIndexService = vectorIndexService;
        this.objectMapper = objectMapper;
    }

    @Async
    public void run(Long taskId, Long projectId) {
        try {
            ResearchProfilePayload profile = profileService.get(projectId);
            if (!profile.confirmed()) throw new IllegalStateException("请先确认研究卡片再检索论文");
            projectService.updateStatus(projectId, ProjectStatus.SEARCHING);
            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.PLANNING_QUERIES, 5, "正在生成检索式");
            searchQueryRepository.deleteByProjectId(projectId);
            clearPreviousPaperResults(projectId);

            // 读取时间范围（从 ResearchProject 中获取，不依赖前端临时参数）
            Integer[] yearRange = projectService.getEffectiveYearRange(projectId);
            Integer startYear = yearRange[0];
            Integer endYear = yearRange[1];

            List<SearchQueryPlan> plans = queryPlanningService.plan(profile);
            if (plans.isEmpty()) throw new IllegalStateException("未能生成有效检索式");
            List<PaperCandidate> candidates = new ArrayList<>();

            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.SEARCHING_OPENALEX, 15, "正在检索 OpenAlex");
            for (int i = 0; i < plans.size(); i++) {
                SearchQueryPlan plan = plans.get(i);
                int count = 0;
                try {
                    List<PaperCandidate> result = openAlexClient.search(plan.query(), startYear, endYear);
                    candidates.addAll(result);
                    count = result.size();
                } catch (Exception ignored) {
                }
                saveQuery(projectId, "OpenAlex", plan, count, startYear, endYear);
                int progress = 15 + (int) ((i + 1) * 20.0 / plans.size());
                taskService.update(taskId, TaskStatus.RUNNING, TaskStage.SEARCHING_OPENALEX, progress,
                        "OpenAlex 检索 " + (i + 1) + "/" + plans.size());
            }

            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.SEARCHING_ARXIV, 38, "正在检索 arXiv");
            for (int i = 0; i < plans.size(); i++) {
                SearchQueryPlan plan = plans.get(i);
                int count = 0;
                try {
                    List<PaperCandidate> result = arxivClient.search(plan.query(), startYear, endYear);
                    candidates.addAll(result);
                    count = result.size();
                } catch (Exception ignored) {
                }
                saveQuery(projectId, "arXiv", plan, count, startYear, endYear);
                int progress = 38 + (int) ((i + 1) * 22.0 / plans.size());
                taskService.update(taskId, TaskStatus.RUNNING, TaskStage.SEARCHING_ARXIV, progress,
                        "arXiv 检索 " + (i + 1) + "/" + plans.size());
            }

            // 后端二次校验：再次过滤超出时间范围的论文（防止 API 过滤不完整）
            if (startYear != null || endYear != null) {
                candidates = candidates.stream().filter(c -> {
                    Integer year = c.getPublicationYear();
                    if (year == null) return false;
                    boolean afterStart = startYear == null || year >= startYear;
                    boolean beforeEnd = endYear == null || year <= endYear;
                    return afterStart && beforeEnd;
                }).toList();
            }

            if (candidates.isEmpty()) throw new IllegalStateException("两个数据源均未返回论文，请检查网络、API Key、检索式或时间范围设置");

            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.DEDUPLICATING, 65, "正在合并和去重论文");
            List<PaperCandidate> deduplicated = deduplicationService.deduplicate(candidates);

            taskService.update(taskId, TaskStatus.RUNNING, TaskStage.RANKING, 75, "正在计算论文相关性");
            List<RankedPaperCandidate> ranked = rankingService.rank(profile, deduplicated, startYear, endYear).stream()
                    .limit(topResults)
                    .toList();
            persistResults(projectId, ranked);

            projectService.updateStatus(projectId, ProjectStatus.SEARCH_COMPLETED);
            taskService.update(taskId, TaskStatus.COMPLETED, TaskStage.COMPLETED, 100,
                    "检索完成，共保留 " + ranked.size() + " 篇论文");
        } catch (Exception e) {
            projectService.updateStatus(projectId, ProjectStatus.FAILED);
            taskService.fail(taskId, e);
        }
    }

    private void clearPreviousPaperResults(Long projectId) {
        for (ProjectPaper projectPaper : projectPaperRepository.findByProjectIdOrderByRankNumberAsc(projectId)) {
            vectorIndexService.deleteFileVectors("project-paper://" + projectPaper.getId());
            evidenceRepository.deleteByProjectPaperId(projectPaper.getId());
            paperAnalysisRepository.deleteByProjectPaperId(projectPaper.getId());
        }
        projectPaperRepository.deleteByProjectId(projectId);
    }

    private void saveQuery(Long projectId, String source, SearchQueryPlan plan, int count, Integer startYear, Integer endYear) {
        SearchQuery query = new SearchQuery();
        query.setProjectId(projectId);
        query.setSource(source);
        query.setQueryType(plan.type());
        query.setQueryText(plan.query());
        query.setResultCount(count);
        query.setPaperStartYear(startYear);
        query.setPaperEndYear(endYear);
        query.setExecutedAt(LocalDateTime.now());
        searchQueryRepository.save(query);
    }

    private void persistResults(Long projectId, List<RankedPaperCandidate> ranked) throws Exception {
        int rank = 1;
        for (RankedPaperCandidate candidate : ranked) {
            Paper paper = findExisting(candidate.paper()).orElseGet(Paper::new);
            mergePaper(paper, candidate.paper());
            paper = paperRepository.save(paper);

            ProjectPaper projectPaper = new ProjectPaper();
            projectPaper.setProjectId(projectId);
            projectPaper.setPaperId(paper.getId());
            projectPaper.setRetrievalSources(String.join(",", candidate.paper().getSources()));
            projectPaper.setEmbeddingScore(candidate.embeddingScore());
            projectPaper.setKeywordScore(candidate.keywordScore());
            projectPaper.setTitleScore(candidate.titleScore());
            projectPaper.setQualityScore(candidate.qualityScore());
            projectPaper.setCoreCoverageScore(candidate.coreCoverageScore());
            projectPaper.setPhraseGroupScore(candidate.phraseGroupScore());
            projectPaper.setAbstractMatchScore(candidate.abstractMatchScore());
            projectPaper.setMatchedCoreKeywordsJson(objectMapper.writeValueAsString(candidate.matchedCoreKeywords()));
            projectPaper.setMatchedMethodKeywordsJson(objectMapper.writeValueAsString(candidate.matchedMethodKeywords()));
            projectPaper.setMatchedKeywordGroupsJson(objectMapper.writeValueAsString(candidate.matchedKeywordGroups()));
            projectPaper.setRecommendationReason(candidate.recommendationReason());
            projectPaper.setFinalScore(candidate.finalScore());
            projectPaper.setRankNumber(rank);
            projectPaper.setSelected(rank <= Math.min(10, ranked.size()));
            projectPaper.setEvidenceLevel(EvidenceLevel.ABSTRACT);
            projectPaperRepository.save(projectPaper);
            rank++;
        }
    }

    private Optional<Paper> findExisting(PaperCandidate candidate) {
        if (candidate.getDoi() != null && !candidate.getDoi().isBlank()) {
            Optional<Paper> found = paperRepository.findFirstByDoiIgnoreCase(candidate.getDoi());
            if (found.isPresent()) return found;
        }
        if (candidate.getArxivId() != null && !candidate.getArxivId().isBlank()) {
            Optional<Paper> found = paperRepository.findFirstByArxivIdIgnoreCase(candidate.getArxivId());
            if (found.isPresent()) return found;
        }
        if (candidate.getOpenalexId() != null && !candidate.getOpenalexId().isBlank()) {
            Optional<Paper> found = paperRepository.findFirstByOpenalexId(candidate.getOpenalexId());
            if (found.isPresent()) return found;
        }
        // 标题标准化相同去重：arXiv 版与正式发表版常标题一致但 DOI/ID 不同。
        if (candidate.getTitle() != null && !candidate.getTitle().isBlank()) {
            Optional<Paper> found = paperRepository.findFirstByTitleIgnoreCase(candidate.getTitle());
            if (found.isPresent() && TitleNormalizer.similarity(found.get().getTitle(), candidate.getTitle()) >= 0.95) {
                return found;
            }
        }
        return Optional.empty();
    }

    private void mergePaper(Paper paper, PaperCandidate candidate) throws Exception {
        if (candidate.getOpenalexId() != null && !candidate.getOpenalexId().isBlank()) paper.setOpenalexId(candidate.getOpenalexId());
        if (candidate.getArxivId() != null && !candidate.getArxivId().isBlank()) paper.setArxivId(candidate.getArxivId());
        if (candidate.getDoi() != null && !candidate.getDoi().isBlank()) paper.setDoi(candidate.getDoi());
        paper.setTitle(candidate.getTitle());
        if (candidate.getAbstractText() != null && !candidate.getAbstractText().isBlank()) paper.setAbstractText(candidate.getAbstractText());
        paper.setAuthorsJson(objectMapper.writeValueAsString(candidate.getAuthors()));
        paper.setPublicationYear(candidate.getPublicationYear());
        paper.setVenue(candidate.getVenue());
        paper.setCitationCount(candidate.getCitationCount());
        paper.setLandingUrl(candidate.getLandingUrl());
        paper.setPdfUrl(candidate.getPdfUrl());
        paper.setSource(String.join(",", candidate.getSources()));
    }
}
