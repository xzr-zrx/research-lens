package org.example.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ApiResponse;
import org.example.dto.BatchKnowledgeRequest;
import org.example.dto.ResearchProfilePayload;
import org.example.dto.SearchPreferenceRequest;
import org.example.entity.*;
import org.example.enums.TaskType;
import org.example.repository.*;
import org.example.service.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/research")
public class ResearchController {
    private final ResearchProjectService projectService;
    private final ResearchInputService inputService;
    private final ResearchProfileService profileService;
    private final AnalysisTaskService taskService;
    private final AcademicSearchWorkflowService searchWorkflow;
    private final PaperAnalysisWorkflowService analysisWorkflow;
    private final SearchQueryRepository queryRepository;
    private final ProjectPaperRepository projectPaperRepository;
    private final PaperRepository paperRepository;
    private final PaperAnalysisRepository analysisRepository;
    private final EvidenceRepository evidenceRepository;
    private final PaperKnowledgeService paperKnowledgeService;
    private final PaperKnowledgeBatchService paperKnowledgeBatchService;
    private final ResearchReportService reportService;
    private final ObjectMapper objectMapper;

    public ResearchController(ResearchProjectService projectService,
                              ResearchInputService inputService,
                              ResearchProfileService profileService,
                              AnalysisTaskService taskService,
                              AcademicSearchWorkflowService searchWorkflow,
                              PaperAnalysisWorkflowService analysisWorkflow,
                              SearchQueryRepository queryRepository,
                              ProjectPaperRepository projectPaperRepository,
                              PaperRepository paperRepository,
                              PaperAnalysisRepository analysisRepository,
                              EvidenceRepository evidenceRepository,
                              PaperKnowledgeService paperKnowledgeService,
                              PaperKnowledgeBatchService paperKnowledgeBatchService,
                              ResearchReportService reportService,
                              ObjectMapper objectMapper) {
        this.projectService = projectService;
        this.inputService = inputService;
        this.profileService = profileService;
        this.taskService = taskService;
        this.searchWorkflow = searchWorkflow;
        this.analysisWorkflow = analysisWorkflow;
        this.queryRepository = queryRepository;
        this.projectPaperRepository = projectPaperRepository;
        this.paperRepository = paperRepository;
        this.analysisRepository = analysisRepository;
        this.evidenceRepository = evidenceRepository;
        this.paperKnowledgeService = paperKnowledgeService;
        this.paperKnowledgeBatchService = paperKnowledgeBatchService;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/projects")
    public ApiResponse<List<Map<String, Object>>> listProjects() {
        return ApiResponse.ok(projectService.listWithTimeRange());
    }

    @PostMapping("/projects")
    public ApiResponse<Map<String, Object>> createProject(@RequestBody(required = false) Map<String, Object> body) {
        String name = body == null ? null : (String) body.get("name");
        String preset = body == null ? null : (String) body.get("paperTimePreset");
        Integer startYear = parseYear(body, "paperStartYear");
        Integer endYear = parseYear(body, "paperEndYear");
        ResearchProject project = projectService.createWithPreference(name, preset, startYear, endYear);
        return ApiResponse.ok("项目已创建", projectService.getWithTimeRange(project.getId()));
    }

    @GetMapping("/projects/{id}")
    public ApiResponse<Map<String, Object>> getProject(@PathVariable Long id) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("project", projectService.getWithTimeRange(id));
        result.put("inputs", inputService.list(id));
        try { result.put("profile", profileService.get(id)); } catch (Exception e) { result.put("profile", null); }
        result.put("tasks", taskService.listByProject(id));
        return ApiResponse.ok(result);
    }

    @DeleteMapping("/projects/{id}")
    public ApiResponse<Void> deleteProject(@PathVariable Long id) {
        projectService.delete(id);
        return ApiResponse.ok("项目已删除", null);
    }

    @PostMapping("/projects/{id}/inputs/text")
    public ApiResponse<ResearchInput> addText(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return ApiResponse.ok(inputService.addText(id, body.get("text")));
    }

    @PostMapping(value = "/projects/{id}/inputs/file", consumes = "multipart/form-data")
    public ApiResponse<ResearchInput> addFile(@PathVariable Long id, @RequestPart("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(inputService.addFile(id, file));
    }

    @PostMapping("/projects/{id}/profile/generate")
    public ApiResponse<ResearchProfilePayload> generateProfile(@PathVariable Long id) {
        return ApiResponse.ok(profileService.generate(id));
    }

    @GetMapping("/projects/{id}/profile")
    public ApiResponse<ResearchProfilePayload> getProfile(@PathVariable Long id) {
        return ApiResponse.ok(profileService.get(id));
    }

    @PutMapping("/projects/{id}/profile")
    public ApiResponse<ResearchProfilePayload> saveProfile(@PathVariable Long id, @RequestBody ResearchProfilePayload payload) {
        return ApiResponse.ok(profileService.save(id, payload));
    }

    @PutMapping("/projects/{id}/search-preference")
    public ApiResponse<Map<String, Object>> updateSearchPreference(@PathVariable Long id, @RequestBody SearchPreferenceRequest body) {
        ResearchProject project = projectService.updateSearchPreference(id,
                body.getPaperTimePreset(), body.getPaperStartYear(), body.getPaperEndYear());
        return ApiResponse.ok("检索时间范围已更新", projectService.getWithTimeRange(project.getId()));
    }

    @PostMapping("/projects/{id}/search")
    public ApiResponse<AnalysisTask> startSearch(@PathVariable Long id) {
        ResearchProfilePayload profile = profileService.get(id);
        if (!profile.confirmed()) throw new IllegalStateException("请先确认研究卡片");
        AnalysisTask task = taskService.create(id, TaskType.PAPER_SEARCH);
        searchWorkflow.run(task.getId(), id);
        return ApiResponse.ok("检索任务已启动", task);
    }

    @GetMapping("/projects/{id}/queries")
    public ApiResponse<List<SearchQuery>> listQueries(@PathVariable Long id) {
        projectService.get(id);
        return ApiResponse.ok(queryRepository.findByProjectIdOrderByIdAsc(id));
    }

    @GetMapping("/projects/{id}/papers")
    public ApiResponse<List<Map<String, Object>>> listPapers(@PathVariable Long id) {
        projectService.get(id);
        List<Map<String, Object>> result = projectPaperRepository.findByProjectIdOrderByRankNumberAsc(id).stream()
                .map(this::paperView).toList();
        return ApiResponse.ok(result);
    }

    @PutMapping("/projects/{id}/papers/{projectPaperId}/selection")
    public ApiResponse<ProjectPaper> selectPaper(@PathVariable Long id,
                                                 @PathVariable Long projectPaperId,
                                                 @RequestBody Map<String, Boolean> body) {
        ProjectPaper pp = projectPaperRepository.findById(projectPaperId)
                .orElseThrow(() -> new IllegalArgumentException("项目论文不存在"));
        if (!pp.getProjectId().equals(id)) throw new IllegalArgumentException("论文不属于当前项目");
        pp.setSelected(Boolean.TRUE.equals(body.get("selected")));
        return ApiResponse.ok(projectPaperRepository.save(pp));
    }

    @PostMapping("/projects/{id}/analyze")
    public ApiResponse<AnalysisTask> startAnalysis(@PathVariable Long id) {
        AnalysisTask task = taskService.create(id, TaskType.PAPER_ANALYSIS);
        analysisWorkflow.run(task.getId(), id);
        return ApiResponse.ok("分析任务已启动", task);
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResponse<AnalysisTask> getTask(@PathVariable Long taskId) {
        return ApiResponse.ok(taskService.get(taskId));
    }

    @GetMapping("/projects/{id}/report")
    public ApiResponse<ResearchReport> getReport(@PathVariable Long id) {
        return ApiResponse.ok(reportService.getLatest(id));
    }

    @PostMapping("/projects/{id}/report/regenerate")
    public ApiResponse<ResearchReport> regenerateReport(@PathVariable Long id) {
        return ApiResponse.ok(reportService.generate(id));
    }

    @PostMapping("/projects/{id}/papers/{projectPaperId}/knowledge")
    public ApiResponse<ProjectPaper> savePaperToKnowledge(@PathVariable Long id, @PathVariable Long projectPaperId) throws Exception {
        requireProjectPaper(id, projectPaperId);
        return ApiResponse.ok("全文已获取并加入知识库", paperKnowledgeService.saveToKnowledge(projectPaperId));
    }

    @PostMapping("/projects/{id}/papers/{projectPaperId}/knowledge/abstract")
    public ApiResponse<ProjectPaper> savePaperAbstract(@PathVariable Long id, @PathVariable Long projectPaperId) throws Exception {
        requireProjectPaper(id, projectPaperId);
        return ApiResponse.ok("已按用户选择仅将摘要加入知识库", paperKnowledgeService.saveAbstractOnly(projectPaperId));
    }

    @PostMapping("/projects/{id}/papers/{projectPaperId}/knowledge/retry")
    public ApiResponse<ProjectPaper> retryPaperKnowledge(@PathVariable Long id, @PathVariable Long projectPaperId) throws Exception {
        requireProjectPaper(id, projectPaperId);
        return ApiResponse.ok(paperKnowledgeService.retryFullText(projectPaperId));
    }

    @PostMapping("/projects/{id}/papers/knowledge/batch")
    public ApiResponse<AnalysisTask> batchKnowledge(@PathVariable Long id,
                                                    @RequestBody(required = false) BatchKnowledgeRequest request) {
        projectService.get(id);
        List<Long> ids = request == null || request.projectPaperIds() == null || request.projectPaperIds().isEmpty()
                ? projectPaperRepository.findByProjectIdAndSelectedTrueOrderByRankNumberAsc(id).stream().map(ProjectPaper::getId).toList()
                : request.projectPaperIds();
        for (Long projectPaperId : ids) requireProjectPaper(id, projectPaperId);
        AnalysisTask task = taskService.create(id, TaskType.PAPER_KNOWLEDGE_BATCH);
        paperKnowledgeBatchService.run(task.getId(), ids);
        return ApiResponse.ok("批量全文入库任务已启动", task);
    }

    @PostMapping(value = "/projects/{id}/papers/{projectPaperId}/fulltext", consumes = "multipart/form-data")
    public ApiResponse<ProjectPaper> uploadFullText(@PathVariable Long id,
                                                    @PathVariable Long projectPaperId,
                                                    @RequestPart("file") MultipartFile file) throws Exception {
        requireProjectPaper(id, projectPaperId);
        return ApiResponse.ok("全文已上传并完成入库", paperKnowledgeService.uploadFullText(projectPaperId, file));
    }

    private ProjectPaper requireProjectPaper(Long projectId, Long projectPaperId) {
        ProjectPaper pp = projectPaperRepository.findById(projectPaperId)
                .orElseThrow(() -> new IllegalArgumentException("项目论文不存在"));
        if (!pp.getProjectId().equals(projectId)) throw new IllegalArgumentException("论文不属于当前项目");
        return pp;
    }

    private Integer parseYear(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) return null;
        try {
            return Integer.valueOf(body.get(key).toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> paperView(ProjectPaper pp) {
        Paper paper = paperRepository.findById(pp.getPaperId()).orElse(null);
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("projectPaper", pp);
        map.put("paper", paper);
        if (paper != null) {
            try {
                map.put("authors", objectMapper.readValue(paper.getAuthorsJson() == null ? "[]" : paper.getAuthorsJson(), new TypeReference<List<String>>() {}));
            } catch (Exception e) {
                map.put("authors", List.of());
            }
        }
        PaperAnalysis analysis = analysisRepository.findByProjectPaperId(pp.getId()).orElse(null);
        map.put("analysis", analysis);
        map.put("evidence", evidenceRepository.findByProjectPaperId(pp.getId()));
        boolean directFullText = paper != null && ((paper.getLocalFullTextPath() != null && !paper.getLocalFullTextPath().isBlank())
                || (paper.getPdfUrl() != null && !paper.getPdfUrl().isBlank())
                || (paper.getArxivId() != null && !paper.getArxivId().isBlank()));
        boolean fetchCandidate = directFullText || paper != null && ((paper.getLandingUrl() != null && !paper.getLandingUrl().isBlank())
                || (paper.getDoi() != null && !paper.getDoi().isBlank()));
        map.put("directFullTextAvailable", directFullText);
        map.put("fullTextAvailable", fetchCandidate);
        map.put("matchedCoreKeywords", readStringList(pp.getMatchedCoreKeywordsJson()));
        map.put("matchedMethodKeywords", readStringList(pp.getMatchedMethodKeywordsJson()));
        map.put("matchedKeywordGroups", readNestedStringList(pp.getMatchedKeywordGroupsJson()));
        return map;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<String>>() {}); }
        catch (Exception e) { return List.of(); }
    }

    private List<List<String>> readNestedStringList(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<List<String>>>() {}); }
        catch (Exception e) { return List.of(); }
    }
}
