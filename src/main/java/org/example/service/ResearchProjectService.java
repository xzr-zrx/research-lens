package org.example.service;

import org.example.entity.ResearchProject;
import org.example.enums.PaperTimePreset;
import org.example.enums.ProjectStatus;
import org.example.exception.NotFoundException;
import org.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResearchProjectService {
    private final ResearchProjectRepository projectRepository;
    private final ResearchInputRepository inputRepository;
    private final ResearchProfileRepository profileRepository;
    private final SearchQueryRepository queryRepository;
    private final ProjectPaperRepository projectPaperRepository;
    private final PaperAnalysisRepository paperAnalysisRepository;
    private final EvidenceRepository evidenceRepository;
    private final AnalysisTaskRepository taskRepository;
    private final ResearchReportRepository reportRepository;
    private final VectorIndexService vectorIndexService;

    public ResearchProjectService(ResearchProjectRepository projectRepository,
                                  ResearchInputRepository inputRepository,
                                  ResearchProfileRepository profileRepository,
                                  SearchQueryRepository queryRepository,
                                  ProjectPaperRepository projectPaperRepository,
                                  PaperAnalysisRepository paperAnalysisRepository,
                                  EvidenceRepository evidenceRepository,
                                  AnalysisTaskRepository taskRepository,
                                  ResearchReportRepository reportRepository,
                                  VectorIndexService vectorIndexService) {
        this.projectRepository = projectRepository;
        this.inputRepository = inputRepository;
        this.profileRepository = profileRepository;
        this.queryRepository = queryRepository;
        this.projectPaperRepository = projectPaperRepository;
        this.paperAnalysisRepository = paperAnalysisRepository;
        this.evidenceRepository = evidenceRepository;
        this.taskRepository = taskRepository;
        this.reportRepository = reportRepository;
        this.vectorIndexService = vectorIndexService;
    }

    public List<ResearchProject> list() {
        return projectRepository.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .toList();
    }

    public ResearchProject get(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("研究项目不存在: " + id));
    }

    public ResearchProject create(String name) {
        return createWithPreference(name, PaperTimePreset.RECENT_5_YEARS.name(), null, null);
    }

    public ResearchProject createWithPreference(String name, String preset, Integer startYear, Integer endYear) {
        ResearchProject project = new ResearchProject();
        project.setName(name == null || name.isBlank() ? "未命名研究项目" : name.trim());
        project.setStatus(ProjectStatus.DRAFT);
        PaperTimePreset p = parsePreset(preset);
        Integer[] resolved = validateAndResolve(p, startYear, endYear);
        project.setPaperTimePreset(p.name());
        project.setPaperStartYear(resolved[0]);
        project.setPaperEndYear(resolved[1]);
        return projectRepository.save(project);
    }

    /**
     * 设置论文检索时间范围。
     */
    @Transactional
    public ResearchProject updateSearchPreference(Long id, String preset, Integer startYear, Integer endYear) {
        ResearchProject project = get(id);
        PaperTimePreset p = parsePreset(preset);
        Integer[] resolved = validateAndResolve(p, startYear, endYear);
        project.setPaperTimePreset(p.name());
        project.setPaperStartYear(resolved[0]);
        project.setPaperEndYear(resolved[1]);
        return projectRepository.save(project);
    }

    public List<Map<String, Object>> listWithTimeRange() {
        return projectRepository.findAll().stream()
                .sorted((a, b) -> b.getUpdatedAt().compareTo(a.getUpdatedAt()))
                .map(p -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("id", p.getId());
                    map.put("name", p.getName());
                    map.put("status", p.getStatus().name());
                    map.put("createdAt", p.getCreatedAt());
                    map.put("updatedAt", p.getUpdatedAt());
                    map.put("paperTimePreset", p.getPaperTimePreset());
                    map.put("paperStartYear", p.getPaperStartYear());
                    map.put("paperEndYear", p.getPaperEndYear());
                    return map;
                })
                .toList();
    }

    /**
     * 以 Map 形式获取项目的完整信息（供 controller 使用）
     */
    public Map<String, Object> getWithTimeRange(Long id) {
        ResearchProject project = get(id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", project.getId());
        result.put("name", project.getName());
        result.put("status", project.getStatus().name());
        result.put("createdAt", project.getCreatedAt());
        result.put("updatedAt", project.getUpdatedAt());
        result.put("paperTimePreset", project.getPaperTimePreset());
        result.put("paperStartYear", project.getPaperStartYear());
        result.put("paperEndYear", project.getPaperEndYear());
        return result;
    }

    /**
     * 计算项目的实际时间范围，ALL_TIME 返回 null。
     */
    public Integer[] getEffectiveYearRange(Long id) {
        ResearchProject project = get(id);
        PaperTimePreset preset = parsePreset(project.getPaperTimePreset());
        if (preset == PaperTimePreset.ALL_TIME) return new Integer[]{null, null};
        Integer start = project.getPaperStartYear();
        Integer end = project.getPaperEndYear();
        // 兼容旧数据：字段为空时按 ALL_TIME
        if (start == null || end == null) return new Integer[]{null, null};
        return new Integer[]{start, end};
    }

    private PaperTimePreset parsePreset(String value) {
        if (value == null || value.isBlank()) return PaperTimePreset.RECENT_5_YEARS;
        try {
            return PaperTimePreset.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return PaperTimePreset.RECENT_5_YEARS;
        }
    }

    private Integer[] validateAndResolve(PaperTimePreset preset, Integer start, Integer end) {
        if (preset == PaperTimePreset.ALL_TIME) return new Integer[]{null, null};
        if (preset == PaperTimePreset.CUSTOM) {
            if (start == null || end == null) throw new IllegalArgumentException("自定义时间范围必须填写开始年份和结束年份");
            if (start > end) throw new IllegalArgumentException("开始年份不能大于结束年份");
            int currentYear = Year.now().getValue();
            if (end > currentYear) throw new IllegalArgumentException("结束年份不能超过当前年份");
            if (start < 1900 || end < 1900) throw new IllegalArgumentException("年份必须为合理的四位整数");
            return new Integer[]{start, end};
        }
        return PaperTimePreset.resolveYears(preset, null, null);
    }

    public ResearchProject updateStatus(Long id, ProjectStatus status) {
        ResearchProject project = get(id);
        project.setStatus(status);
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        for (var pp : projectPaperRepository.findByProjectIdOrderByRankNumberAsc(id)) {
            vectorIndexService.deleteFileVectors("project-paper://" + pp.getId());
            evidenceRepository.deleteByProjectPaperId(pp.getId());
            paperAnalysisRepository.deleteByProjectPaperId(pp.getId());
        }
        projectPaperRepository.deleteByProjectId(id);
        taskRepository.deleteByProjectId(id);
        reportRepository.deleteByProjectId(id);
        queryRepository.deleteByProjectId(id);
        profileRepository.deleteByProjectId(id);
        inputRepository.deleteByProjectId(id);
        projectRepository.deleteById(id);
    }
}
