package org.example.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ResearchProfilePayload;
import org.example.entity.*;
import org.example.enums.PaperTimePreset;
import org.example.enums.RelationType;
import org.example.repository.*;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

@Service
public class ResearchReportService {
    private final ResearchProfileService profileService;
    private final ResearchProjectRepository projectRepository;
    private final SearchQueryRepository queryRepository;
    private final ProjectPaperRepository projectPaperRepository;
    private final PaperRepository paperRepository;
    private final PaperAnalysisRepository analysisRepository;
    private final EvidenceRepository evidenceRepository;
    private final ResearchReportRepository reportRepository;
    private final ObjectMapper objectMapper;

    public ResearchReportService(ResearchProfileService profileService,
                                 ResearchProjectRepository projectRepository,
                                 SearchQueryRepository queryRepository,
                                 ProjectPaperRepository projectPaperRepository,
                                 PaperRepository paperRepository,
                                 PaperAnalysisRepository analysisRepository,
                                 EvidenceRepository evidenceRepository,
                                 ResearchReportRepository reportRepository,
                                 ObjectMapper objectMapper) {
        this.profileService = profileService;
        this.projectRepository = projectRepository;
        this.queryRepository = queryRepository;
        this.projectPaperRepository = projectPaperRepository;
        this.paperRepository = paperRepository;
        this.analysisRepository = analysisRepository;
        this.evidenceRepository = evidenceRepository;
        this.reportRepository = reportRepository;
        this.objectMapper = objectMapper;
    }

    public ResearchReport getLatest(Long projectId) {
        return reportRepository.findFirstByProjectIdOrderByVersionDesc(projectId)
                .orElseThrow(() -> new IllegalStateException("该项目尚未生成报告"));
    }

    public ResearchReport generate(Long projectId) {
        ResearchProfilePayload profile = profileService.get(projectId);
        ResearchProject project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("项目不存在"));
        List<SearchQuery> queries = queryRepository.findByProjectIdOrderByIdAsc(projectId);
        List<ProjectPaper> projectPapers = projectPaperRepository.findByProjectIdAndSelectedTrueOrderByRankNumberAsc(projectId);
        List<PaperBundle> bundles = new ArrayList<>();
        for (ProjectPaper pp : projectPapers) {
            Paper paper = paperRepository.findById(pp.getPaperId()).orElse(null);
            if (paper == null) continue;
            PaperAnalysis analysis = analysisRepository.findByProjectPaperId(pp.getId()).orElse(null);
            List<Evidence> evidence = evidenceRepository.findByProjectPaperId(pp.getId());
            bundles.add(new PaperBundle(pp, paper, analysis, evidence));
        }

        String markdown = buildMarkdown(profile, queries, bundles, project);
        ResearchReport report = new ResearchReport();
        report.setProjectId(projectId);
        int version = reportRepository.findFirstByProjectIdOrderByVersionDesc(projectId)
                .map(r -> r.getVersion() + 1).orElse(1);
        report.setVersion(version);
        report.setReportMarkdown(markdown);
        try {
            report.setReportJson(objectMapper.writeValueAsString(Map.of(
                    "profile", profile,
                    "queries", queries,
                    "paperCount", bundles.size(),
                    "generatedAt", LocalDate.now().toString()
            )));
        } catch (Exception e) {
            report.setReportJson("{}");
        }
        return reportRepository.save(report);
    }

    private String buildMarkdown(ResearchProfilePayload profile, List<SearchQuery> queries, List<PaperBundle> bundles, ResearchProject project) {
        StringBuilder md = new StringBuilder();
        md.append("# 科研想法与相关工作分析报告\n\n");
        md.append("> 生成日期：").append(LocalDate.now()).append("\n\n");

        md.append("## 1. 用户想法理解\n\n");
        md.append("- **研究领域：** ").append(safe(profile.domain())).append("\n");
        md.append("- **研究对象：** ").append(safe(profile.researchObject())).append("\n");
        md.append("- **研究问题：** ").append(safe(profile.researchProblem())).append("\n");
        md.append("- **拟采用方法：** ").append(safe(profile.proposedMethod())).append("\n");
        md.append("- **英文关键词：** ").append(String.join("、", safeList(profile.keywordsEn()))).append("\n\n");

        md.append("## 2. 检索范围\n\n");
        long openAlexCount = queries.stream().filter(q -> "OpenAlex".equalsIgnoreCase(q.getSource())).mapToInt(q -> Optional.ofNullable(q.getResultCount()).orElse(0)).sum();
        long arxivCount = queries.stream().filter(q -> "arXiv".equalsIgnoreCase(q.getSource())).mapToInt(q -> Optional.ofNullable(q.getResultCount()).orElse(0)).sum();
        md.append("- 数据源：OpenAlex、arXiv\n");
        md.append("- OpenAlex 原始返回：").append(openAlexCount).append(" 条\n");
        md.append("- arXiv 原始返回：").append(arxivCount).append(" 条\n");
        md.append("- 进入详细分析：").append(bundles.size()).append(" 篇\n");
        if (project.getPaperStartYear() != null && project.getPaperEndYear() != null) {
            md.append("- 论文时间范围：").append(project.getPaperStartYear()).append("—").append(project.getPaperEndYear()).append("\n");
        }
        md.append("- 当前分析包含摘要级与用户上传全文级证据。\n\n");

        md.append("## 3. 使用的检索式\n\n");
        for (SearchQuery query : queries) {
            md.append("- `").append(query.getSource()).append(" / ").append(query.getQueryType()).append("`：")
                    .append(query.getQueryText()).append("（返回 ").append(query.getResultCount()).append("）\n");
        }
        md.append('\n');

        appendGroup(md, "## 4. 高度相关工作", bundles, RelationType.HIGHLY_RELATED);
        appendGroup(md, "## 5. 同问题但方法不同", bundles, RelationType.SAME_PROBLEM_DIFFERENT_METHOD);
        appendGroup(md, "## 6. 方法相似但领域不同", bundles, RelationType.SIMILAR_METHOD_DIFFERENT_FIELD);
        appendGroup(md, "## 7. 间接启发", bundles, RelationType.INDIRECT_REFERENCE);

        md.append("## 8. 用户方案与论文对比表\n\n");
        md.append("| 排名 | 论文 | 年份 | 关系类型 | 相关性 | 证据级别 | 主要关系 |\n");
        md.append("|---:|---|---:|---|---:|---|---|\n");
        for (PaperBundle b : bundles) {
            String relation = b.analysis == null ? "未完成" : String.valueOf(b.analysis.getRelationType());
            String explanation = b.analysis == null ? safe(b.analysis == null ? "分析失败或未生成" : b.analysis.getRelationToUserIdea()) : safe(b.analysis.getRelationToUserIdea());
            md.append('|').append(b.projectPaper.getRankNumber()).append('|')
                    .append(escape(b.paper.getTitle())).append('|')
                    .append(Optional.ofNullable(b.paper.getPublicationYear()).map(String::valueOf).orElse("-")).append('|')
                    .append(relation).append('|')
                    .append(String.format("%.3f", b.projectPaper.getFinalScore())).append('|')
                    .append(b.projectPaper.getEvidenceLevel()).append('|')
                    .append(escape(shorten(explanation, 120))).append("|\n");
        }
        md.append('\n');

        md.append("## 9. 已有工作覆盖情况\n\n");
        if (bundles.isEmpty()) {
            md.append("当前没有可用于综合的论文分析结果。\n\n");
        } else {
            md.append("本次结果表明，已有工作主要围绕以下问题和方法展开：\n\n");
            for (PaperBundle b : bundles.stream().limit(5).toList()) {
                String method = b.analysis == null ? "待阅读全文确认" : safe(b.analysis.getMethod());
                md.append("- **").append(b.paper.getTitle()).append("**：").append(shorten(method, 220)).append("\n");
            }
            md.append('\n');
        }

        md.append("## 10. 可能存在的差异\n\n");
        md.append("在本次检索数据库、检索时间和检索式范围内，用户方案与已有工作的差异需要重点从优化变量、实验条件、是否联合估计、是否需要训练以及评价方式等方面确认。当前自动报告只提供线索，不能直接证明创新性。\n\n");

        md.append("## 11. 风险和待确认问题\n\n");
        md.append("- 是否存在使用不同术语描述、但实质相同的工作；\n");
        md.append("- 摘要未披露的关键实现是否会改变相似性判断；\n");
        md.append("- 用户方案中的变量是否可辨识，是否存在退化解；\n");
        md.append("- 是否需要扩大数据库、年份、相邻领域和引用网络检索；\n");
        md.append("- 排名前列论文应优先上传全文后再次分析。\n\n");

        md.append("## 12. 推荐阅读顺序\n\n");
        for (PaperBundle b : bundles.stream().sorted(Comparator.comparingInt(x -> x.projectPaper.getRankNumber())).limit(5).toList()) {
            md.append(b.projectPaper.getRankNumber()).append(". ").append(b.paper.getTitle());
            if (b.paper.getLandingUrl() != null && !b.paper.getLandingUrl().isBlank()) md.append("（").append(b.paper.getLandingUrl()).append("）");
            md.append("\n");
        }
        md.append('\n');

        md.append("## 13. 检索限制说明\n\n");
        md.append("> 本次结果受数据库、检索式和论文时间范围限制。本报告只能说明在本次检索数据库、检索日期和检索式范围内发现的相关研究，不能证明不存在未收录、未公开、付费全文中隐藏或采用不同术语的相关工作。摘要级结论应通过阅读全文核对；报告不能替代正式的新颖性检索或专家评审。\n");
        return md.toString();
    }

    private void appendGroup(StringBuilder md, String title, List<PaperBundle> bundles, RelationType type) {
        md.append(title).append("\n\n");
        List<PaperBundle> group = bundles.stream().filter(b -> b.analysis != null && b.analysis.getRelationType() == type).toList();
        if (group.isEmpty()) {
            md.append("本次分析中没有归入该类别的论文。\n\n");
            return;
        }
        for (PaperBundle b : group) {
            md.append("### ").append(b.paper.getTitle()).append("\n\n");
            md.append("- 年份：").append(Optional.ofNullable(b.paper.getPublicationYear()).map(String::valueOf).orElse("未知")).append("\n");
            md.append("- 相关性得分：").append(String.format("%.3f", b.projectPaper.getFinalScore())).append("\n");
            md.append("- 与用户方案关系：").append(safe(b.analysis.getRelationToUserIdea())).append("\n");
            md.append("- 方法：").append(safe(b.analysis.getMethod())).append("\n");
            md.append("- 局限：").append(safe(b.analysis.getLimitations())).append("\n");
            if (!b.evidence.isEmpty()) {
                md.append("- 证据：\n");
                for (Evidence e : b.evidence.stream().limit(2).toList()) {
                    md.append("  - “").append(shorten(e.getContent(), 300)).append("”\n");
                }
            }
            md.append('\n');
        }
    }

    private String safe(String value) { return value == null || value.isBlank() ? "未明确" : value; }
    private List<String> safeList(List<String> values) { return values == null ? List.of() : values; }
    private String shorten(String value, int max) { String s = safe(value).replaceAll("\\s+", " "); return s.length() <= max ? s : s.substring(0, max) + "…"; }
    private String escape(String value) { return safe(value).replace("|", "\\|").replace("\n", " "); }

    private record PaperBundle(ProjectPaper projectPaper, Paper paper, PaperAnalysis analysis, List<Evidence> evidence) {}
}
