package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ResearchProfilePayload;
import org.example.dto.SearchQueryPlan;
import org.example.enums.QueryType;
import org.example.util.JsonUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@Service
public class AcademicQueryPlanningService {
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    @Value("${academic.search.query-count:7}")
    private int queryCount;

    public AcademicQueryPlanningService(ChatService chatService, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    public List<SearchQueryPlan> plan(ResearchProfilePayload profile) {
        if (chatService.isConfigured()) {
            try {
                List<SearchQueryPlan> plans = llmPlan(profile);
                if (!plans.isEmpty()) return normalize(plans);
            } catch (Exception ignored) { }
        }
        return fallback(profile);
    }

    private List<SearchQueryPlan> llmPlan(ResearchProfilePayload profile) throws Exception {
        String prompt = """
                根据下面研究卡片生成适合 OpenAlex 与 arXiv 的英文检索式。
                严格返回 JSON 数组，每项格式：
                {"type":"CORE_PROBLEM|PROPOSED_METHOD|PROBLEM_VARIANT|RELATED_FIELD","query":"..."}。

                共生成 %d 条，并满足：
                1. 每条至少包含一个核心对象词，以及一个问题词、方法词或扩展词；
                2. 覆盖核心对象+核心问题、核心对象+方法、核心问题+方法、模型失配/校准、邻近领域迁移、缩写、精确短语；
                3. 可以使用双引号和 AND 表达组合，但不要使用数据库专属字段语法；
                4. 不要生成只有一个宽泛关键词的检索式；
                5. 避免 excludedKeywords 中的方向。

                研究领域：%s
                研究问题：%s
                拟采用方法：%s
                核心关键词：%s
                方法关键词：%s
                扩展关键词：%s
                排除关键词：%s
                关键词组合：%s
                邻近领域：%s
                """.formatted(queryCount, profile.domain(), profile.researchProblem(), profile.proposedMethod(),
                profile.coreKeywords(), profile.methodKeywords(), profile.expandedKeywords(), profile.excludedKeywords(),
                profile.keywordGroups(), profile.relatedFields());
        String raw = chatService.ask("你是学术检索查询规划器，只输出严格 JSON。", prompt);
        JsonNode array = objectMapper.readTree(JsonUtils.extractJsonArray(raw));
        List<SearchQueryPlan> plans = new ArrayList<>();
        if (array.isArray()) {
            for (JsonNode item : array) {
                String query = item.path("query").asText("").trim();
                if (query.isBlank()) continue;
                QueryType type;
                try { type = QueryType.valueOf(item.path("type").asText("CORE_PROBLEM")); }
                catch (Exception e) { type = QueryType.CORE_PROBLEM; }
                plans.add(new SearchQueryPlan(type, query));
            }
        }
        return plans;
    }

    private List<SearchQueryPlan> fallback(ResearchProfilePayload profile) {
        List<SearchQueryPlan> plans = new ArrayList<>();
        List<String> core = nonEmpty(profile.coreKeywords(), profile.keywordsEn());
        List<String> methods = nonEmpty(profile.methodKeywords(), List.of(keyPhrase(profile.proposedMethod())));
        List<String> expanded = nonEmpty(profile.expandedKeywords(), profile.relatedFields());
        String c1 = at(core, 0, safe(profile.domain()));
        String c2 = at(core, 1, keyPhrase(profile.researchProblem()));
        String m1 = at(methods, 0, keyPhrase(profile.proposedMethod()));
        String m2 = at(methods, 1, "joint optimization");
        String e1 = at(expanded, 0, "model mismatch calibration");

        plans.add(new SearchQueryPlan(QueryType.CORE_PROBLEM, phrase(c1) + " AND " + phrase(c2)));
        plans.add(new SearchQueryPlan(QueryType.PROPOSED_METHOD, phrase(c1) + " AND " + phrase(m1)));
        plans.add(new SearchQueryPlan(QueryType.PROPOSED_METHOD, phrase(c2) + " AND " + phrase(m2)));
        plans.add(new SearchQueryPlan(QueryType.PROBLEM_VARIANT, phrase(c1) + " AND " + phrase(e1)));

        for (List<String> group : safeGroups(profile.keywordGroups())) {
            if (plans.size() >= queryCount) break;
            plans.add(new SearchQueryPlan(QueryType.PROBLEM_VARIANT,
                    group.stream().limit(3).map(this::phrase).reduce((a, b) -> a + " AND " + b).orElse("")));
        }
        for (String field : safeList(profile.relatedFields())) {
            if (plans.size() >= queryCount) break;
            plans.add(new SearchQueryPlan(QueryType.RELATED_FIELD, phrase(c1) + " AND " + phrase(field)));
        }
        for (String keyword : core) {
            if (plans.size() >= queryCount) break;
            if (isAbbreviation(keyword)) plans.add(new SearchQueryPlan(QueryType.CORE_PROBLEM, keyword + " AND " + m1));
        }
        return normalize(plans);
    }

    private List<SearchQueryPlan> normalize(List<SearchQueryPlan> plans) {
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<SearchQueryPlan> result = new ArrayList<>();
        for (SearchQueryPlan plan : plans) {
            String query = plan.query() == null ? "" : plan.query().replaceAll("\\s+", " ").trim();
            if (query.length() > 300) query = query.substring(0, 300);
            String key = query.toLowerCase();
            if (!query.isBlank() && seen.add(key)) result.add(new SearchQueryPlan(plan.type(), query));
            if (result.size() >= queryCount) break;
        }
        return result;
    }

    private String phrase(String value) {
        String v = safe(value).replace("\"", "").trim();
        if (v.isBlank()) return "";
        return v.contains(" ") ? "\"" + v + "\"" : v;
    }
    private boolean isAbbreviation(String value) { return value != null && value.matches("[A-Z0-9-]{2,10}"); }
    private List<String> nonEmpty(List<String> preferred, List<String> fallback) {
        List<String> p = safeList(preferred).stream().filter(v -> !v.isBlank()).toList();
        return p.isEmpty() ? safeList(fallback).stream().filter(v -> !v.isBlank()).toList() : p;
    }
    private String at(List<String> list, int index, String fallback) { return list.size() > index ? list.get(index) : fallback; }
    private List<String> safeList(List<String> values) { return values == null ? List.of() : values; }
    private List<List<String>> safeGroups(List<List<String>> values) { return values == null ? List.of() : values; }
    private String safe(String value) { return value == null ? "" : value; }
    private String keyPhrase(String value) {
        if (value == null || value.isBlank()) return "";
        String clean = value.replaceAll("[^A-Za-z0-9+ -]", " ").replaceAll("\\s+", " ").trim();
        String[] parts = clean.split(" ");
        return String.join(" ", java.util.Arrays.copyOf(parts, Math.min(parts.length, 8)));
    }
}
