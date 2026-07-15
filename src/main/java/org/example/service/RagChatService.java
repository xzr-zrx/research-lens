package org.example.service;

import org.example.dto.ChatDiagnostics;
import org.example.dto.ChatResponse;
import org.example.dto.SourceReference;
import org.example.enums.EvidenceLevel;
import org.example.enums.ChatScope;
import org.example.entity.ProjectPaper;
import org.example.repository.ProjectPaperRepository;
import org.example.util.NumericEvidenceValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

@Service
public class RagChatService {
    private static final Set<String> DATA_SECTIONS = Set.of(
            "Results", "Result", "Evaluation", "Experiments", "Experiment",
            "Experimental Setup", "Setup", "Table Caption", "Appendix", "Discussion");

    private static final Pattern DATA_QUERY_KEYWORDS = Pattern.compile(
            "(SR|SPL|NE|Oracle|成功率|实验数据|实验结果|指标|数值|结果|性能|对比|table|表\\s*\\d|表格|准确率|误差|参数|baseline)",
            Pattern.CASE_INSENSITIVE);

    private final VectorSearchService vectorSearchService;
    private final VectorEmbeddingService embeddingService;
    private final ChatService chatService;
    private final ProjectPaperRepository projectPaperRepository;

    @Value("${rag.top-k:8}")
    private int topK;

    @Value("${rag.history-rounds:6}")
    private int historyRounds;

    public RagChatService(VectorSearchService vectorSearchService,
                          VectorEmbeddingService embeddingService,
                          ChatService chatService,
                          ProjectPaperRepository projectPaperRepository) {
        this.vectorSearchService = vectorSearchService;
        this.embeddingService = embeddingService;
        this.chatService = chatService;
        this.projectPaperRepository = projectPaperRepository;
    }

    public ChatResponse chat(String question, List<Map<String, String>> history) {
        return chat(question, history, ChatScope.ALL_KNOWLEDGE, null, List.of());
    }

    public ChatResponse chat(String question, List<Map<String, String>> history,
                             ChatScope scope, Long projectId, List<Long> requestedPaperIds) {
        if (!embeddingService.isConfigured()) {
            return new ChatResponse("请先在 application.yml 中将 YOUR_DASHSCOPE_API_KEY 替换为自己的 DashScope API Key，知识库检索需要使用 Embedding。", List.of(), null);
        }

        boolean dataQuery = isExperimentalDataQuery(question);
        int searchK = Math.max(topK, dataQuery ? 16 : 10);
        ChatScope effectiveScope = scope == null ? ChatScope.ALL_KNOWLEDGE : scope;
        List<Long> paperIds = resolvePaperIds(effectiveScope, projectId, requestedPaperIds);
        List<VectorSearchService.SearchResult> raw = scopedSearch(question, searchK, effectiveScope, projectId, paperIds);
        List<VectorSearchService.SearchResult> results = routeAndRank(raw, dataQuery);

        List<SourceReference> sources = results.stream().map(this::toSource).toList();

        boolean fullTextAvailable = results.stream().anyMatch(r -> isFullText(r));
        int abstractHits = (int) results.stream().filter(r -> !isFullText(r)).count();
        int fullTextHits = (int) results.stream().filter(r -> isFullText(r) && !isTable(r)).count();
        int tableHits = (int) results.stream().filter(r -> isTable(r)).count();

        if (results.isEmpty()) {
            return new ChatResponse("知识库中没有检索到足够相关的资料。请先上传实验记录、论文全文，或将检索结果加入知识库。", sources,
                    diagnostics(abstractHits, fullTextHits, tableHits, dataQuery, false, List.of()));
        }

        // 仅摘要却问实验数据：明确证据不足，不补写不存在的数字。
        if (dataQuery && !fullTextAvailable) {
            String answer = "⚠️ 当前知识库中该论文仅有**摘要级证据**，未上传全文，无法获得 SR、SPL、NE、Oracle SR 等具体实验数据。\n\n" +
                    "摘要通常不包含完整实验指标。请上传论文 PDF 全文后再提问；上传后系统会从 Results / Experimental Setup / Table 章节返回带页码的具体数值。\n\n" +
                    "已检索到的摘要片段见下方来源，仅供参考，不得据此推导实验数字。";
            return new ChatResponse(answer, sources,
                    diagnostics(abstractHits, fullTextHits, tableHits, dataQuery, false, List.of()));
        }

        StringBuilder context = new StringBuilder();
        for (int i = 0; i < results.size(); i++) {
            VectorSearchService.SearchResult result = results.get(i);
            Map<String, Object> meta = result.getMetadata();
            context.append("[来源 ").append(i + 1).append("]\n");
            context.append("标题: ").append(string(meta.get("title"))).append('\n');
            context.append("章节: ").append(string(meta.get("section"))).append('\n');
            if (meta.get("page") != null) context.append("页码: ").append(meta.get("page")).append('\n');
            if (meta.get("tableNumber") != null) context.append("表格编号: ").append(meta.get("tableNumber")).append('\n');
            context.append("证据级别: ").append(string(meta.getOrDefault("evidenceLevel", "FULL_TEXT"))).append('\n');
            context.append("内容: ").append(result.getContent()).append("\n\n");
        }

        String historyText = buildHistory(history);
        String answer;
        if (!chatService.isConfigured()) {
            StringBuilder fallback = new StringBuilder("已找到以下相关资料，但尚未配置 DashScope Key，暂时无法生成综合回答：\n\n");
            for (int i = 0; i < Math.min(3, sources.size()); i++) {
                fallback.append(i + 1).append(". ").append(sources.get(i).titleOrFilename()).append("：")
                        .append(sources.get(i).contentSnippet()).append("\n\n");
            }
            answer = fallback.toString();
        } else {
            String system = systemPrompt(dataQuery);
            String prompt = """
                    最近对话：
                    %s

                    知识库检索结果：
                    %s

                    用户问题：%s
                    """.formatted(historyText, context, question);
            answer = chatService.ask(system, prompt);
        }

        // 数值证据校验：回答中的数字必须能在检索片段原文中找到。
        List<String> snippets = results.stream().map(VectorSearchService.SearchResult::getContent).toList();
        List<String> unverified = NumericEvidenceValidator.findUnverified(answer, snippets);
        if (!unverified.isEmpty()) {
            answer = answer + "\n\n⚠️ 数值证据校验：以下数值未在检索原文片段中找到，可能为模型推导或编造，请回原文核对："
                    + String.join("、", unverified)
                    + "\n（系统默认禁止根据原文计算派生数据；如确需换算请明确标注“根据原文计算”。）";
        }

        return new ChatResponse(answer, sources,
                diagnostics(abstractHits, fullTextHits, tableHits, dataQuery, fullTextAvailable, unverified));
    }

    private List<Long> resolvePaperIds(ChatScope scope, Long projectId, List<Long> requested) {
        if (scope == ChatScope.SELECTED_PAPERS) {
            if (projectId == null) throw new IllegalArgumentException("选择当前勾选论文时必须提供 projectId");
            return projectPaperRepository.findByProjectIdAndSelectedTrueOrderByRankNumberAsc(projectId).stream()
                    .map(ProjectPaper::getPaperId).distinct().toList();
        }
        if (scope == ChatScope.SPECIFIC_PAPERS) {
            if (projectId == null) throw new IllegalArgumentException("指定论文问答必须提供 projectId");
            if (requested == null || requested.isEmpty()) throw new IllegalArgumentException("请至少选择一篇论文");
            List<Long> ids = requested.stream().filter(Objects::nonNull).distinct().toList();
            if (projectPaperRepository.findByProjectIdAndPaperIdIn(projectId, ids).size() != ids.size()) {
                throw new IllegalArgumentException("部分论文不属于当前项目");
            }
            return ids;
        }
        return List.of();
    }

    private List<VectorSearchService.SearchResult> scopedSearch(String question, int searchK, ChatScope scope,
                                                                 Long projectId, List<Long> paperIds) {
        if (scope == ChatScope.CURRENT_PROJECT) {
            if (projectId == null) throw new IllegalArgumentException("当前项目问答必须提供 projectId");
            return vectorSearchService.searchSimilarDocuments(question, searchK, projectExpr(projectId));
        }
        if (scope == ChatScope.SPECIFIC_PAPERS || scope == ChatScope.SELECTED_PAPERS) {
            if (paperIds.isEmpty()) return List.of();
            int perPaper = Math.max(3, (int) Math.ceil(searchK * 1.0 / paperIds.size()));
            LinkedHashMap<String, VectorSearchService.SearchResult> merged = new LinkedHashMap<>();
            for (Long paperId : paperIds) {
                for (VectorSearchService.SearchResult result : vectorSearchService.searchSimilarDocuments(question, perPaper, projectPaperExpr(projectId, paperId))) {
                    merged.merge(result.getId(), result, (a, b) -> a.getScore() >= b.getScore() ? a : b);
                }
            }
            return merged.values().stream().sorted(Comparator.comparingDouble(VectorSearchService.SearchResult::getScore).reversed())
                    .limit(Math.max(searchK, perPaper * paperIds.size())).toList();
        }
        return vectorSearchService.searchSimilarDocuments(question, searchK);
    }

    private String projectExpr(Long projectId) { return "metadata[\"projectId\"] == " + projectId; }
    private String projectPaperExpr(Long projectId, Long paperId) {
        return projectExpr(projectId) + " and metadata[\"paperId\"] == " + paperId;
    }

    private String systemPrompt(boolean dataQuery) {
        String base = """
                你是个人科研知识助手。只能依据提供的知识库片段回答，不得使用片段以外的知识。

                严格规则：
                1. 每一个具体数值（SR、SPL、NE、成功率、百分比、参数量等）必须逐字来自某个检索片段原文，不得自行换算或补全。
                2. 不允许根据“2×”自动生成“+100%”“减少 50%”等推导值；不允许把定性描述（如“显著提升”）写成定量结果。
                3. 默认禁止任何计算；若必须换算，必须明确标注“根据原文计算”并给出原始数值。
                4. 原文没有该数值时，直接写“论文当前可检索内容未提供具体数值”，不要猜测。
                5. 每个关键结论用“来源1、来源2”标注依据，并在涉及具体数值时同时给出章节、页码、表格编号（若片段中提供）。
                6. 区分摘要级证据与全文级证据；摘要不得作为具体实验数据的来源。
                """;
        if (dataQuery) {
            base += "\n本次提问针对实验数据/指标。优先采信证据级别为 FULL_TEXT、章节为 Results/Experimental Setup/Table/Appendix 的片段；摘要片段只能作为背景补充，不得从中引用具体实验数值。\n";
        }
        return base;
    }

    /** 检索路由：实验数据查询优先 FULL_TEXT + Results/Table/Appendix，摘要作为补充。 */
    private List<VectorSearchService.SearchResult> routeAndRank(List<VectorSearchService.SearchResult> raw, boolean dataQuery) {
        LinkedHashMap<String, VectorSearchService.SearchResult> unique = new LinkedHashMap<>();
        for (VectorSearchService.SearchResult result : raw) {
            Map<String, Object> metadata = result.getMetadata();
            String paperId = string(metadata.get("paperId"));
            String chunkIndex = string(metadata.get("chunkIndex"));
            String key = paperId.isBlank() ? result.getId() : paperId + ":" + chunkIndex + ":" + string(metadata.get("section"));
            unique.merge(key, result, (a, b) -> a.getScore() >= b.getScore() ? a : b);
        }
        List<VectorSearchService.SearchResult> sorted = new ArrayList<>(unique.values());
        if (dataQuery) sorted.sort((a, b) -> Integer.compare(priority(b), priority(a)));
        else sorted.sort(Comparator.comparingDouble(VectorSearchService.SearchResult::getScore).reversed());
        return sorted;
    }

    private int priority(VectorSearchService.SearchResult r) {
        Map<String, Object> meta = r.getMetadata();
        int p = 0;
        String chunkType = string(meta.get("chunkType"));
        String section = string(meta.get("section"));
        String level = string(meta.getOrDefault("evidenceLevel", "FULL_TEXT"));
        if ("TABLE".equals(chunkType) || "Table Caption".equalsIgnoreCase(section)) p += 100;
        if ("FULL_TEXT".equalsIgnoreCase(level)) p += 50;
        if (DATA_SECTIONS.stream().anyMatch(s -> s.equalsIgnoreCase(section))) p += 30;
        if (r.getContent() != null && r.getContent().matches(".*\\d+.*")) p += 5;
        p += Math.round(r.getScore() * 10f);
        return p;
    }

    private boolean containsDigit(String s) { return s != null && s.matches(".*\\d+.*"); }

    private boolean isFullText(VectorSearchService.SearchResult r) {
        String level = string(r.getMetadata().getOrDefault("evidenceLevel", "FULL_TEXT"));
        return "FULL_TEXT".equalsIgnoreCase(level);
    }

    private boolean isTable(VectorSearchService.SearchResult r) {
        String ct = string(r.getMetadata().get("chunkType"));
        String section = string(r.getMetadata().get("section"));
        return "TABLE".equals(ct) || "Table Caption".equalsIgnoreCase(section);
    }

    private boolean isExperimentalDataQuery(String question) {
        if (question == null || question.isBlank()) return false;
        return DATA_QUERY_KEYWORDS.matcher(question).find();
    }

    private ChatDiagnostics diagnostics(int abstractHits, int fullTextHits, int tableHits,
                                        boolean dataQuery, boolean fullTextAvailable,
                                        List<String> unverified) {
        int abstractPapers = (int) projectPaperRepository.countByEvidenceLevel(EvidenceLevel.ABSTRACT);
        int fullTextPapers = (int) projectPaperRepository.countByEvidenceLevel(EvidenceLevel.FULL_TEXT);
        int fullTextChunks = vectorSearchService.countByExpr("metadata[\"chunkType\"] == \"FULL_TEXT\"");
        return new ChatDiagnostics(abstractPapers, fullTextPapers, fullTextChunks,
                abstractHits, fullTextHits, tableHits, dataQuery, fullTextAvailable, unverified);
    }

    private String buildHistory(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) return "无";
        int maxMessages = historyRounds * 2;
        List<Map<String, String>> tail = history.size() <= maxMessages ? history : history.subList(history.size() - maxMessages, history.size());
        StringBuilder builder = new StringBuilder();
        for (Map<String, String> item : tail) {
            builder.append(item.getOrDefault("role", "user")).append(": ")
                    .append(item.getOrDefault("content", "")).append('\n');
        }
        return builder.toString();
    }

    private SourceReference toSource(VectorSearchService.SearchResult result) {
        Map<String, Object> metadata = result.getMetadata();
        String title = string(metadata.get("title"));
        if (title.isBlank()) title = string(metadata.get("_file_name"));
        if (title.isBlank()) title = "未知来源";
        String snippet = result.getContent() == null ? "" : result.getContent().replaceAll("\\s+", " ").trim();
        if (snippet.length() > 320) snippet = snippet.substring(0, 320) + "…";
        return new SourceReference(
                title,
                string(metadata.get("section")),
                integer(metadata.get("page")),
                string(metadata.get("tableNumber")),
                string(metadata.getOrDefault("evidenceLevel", "FULL_TEXT")),
                string(metadata.get("chunkType")),
                snippet,
                result.getScore(),
                string(metadata.get("_source"))
        );
    }

    private String string(Object value) { return value == null ? "" : String.valueOf(value); }
    private Integer integer(Object value) {
        if (value instanceof Number n) return n.intValue();
        try { return value == null ? null : Integer.valueOf(value.toString()); } catch (Exception e) { return null; }
    }
}
