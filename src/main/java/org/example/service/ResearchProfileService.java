package org.example.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.ResearchProfilePayload;
import org.example.entity.ResearchProfile;
import org.example.enums.ProjectStatus;
import org.example.exception.NotFoundException;
import org.example.repository.ResearchProfileRepository;
import org.example.util.JsonUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ResearchProfileService {
    private final ResearchProfileRepository repository;
    private final ResearchInputService inputService;
    private final ResearchProjectService projectService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

    public ResearchProfileService(ResearchProfileRepository repository,
                                  ResearchInputService inputService,
                                  ResearchProjectService projectService,
                                  ChatService chatService,
                                  ObjectMapper objectMapper) {
        this.repository = repository;
        this.inputService = inputService;
        this.projectService = projectService;
        this.chatService = chatService;
        this.objectMapper = objectMapper;
    }

    public ResearchProfile getEntity(Long projectId) {
        return repository.findByProjectId(projectId)
                .orElseThrow(() -> new NotFoundException("该项目尚未生成研究卡片"));
    }

    public ResearchProfilePayload get(Long projectId) { return toPayload(getEntity(projectId)); }

    @Transactional
    public ResearchProfilePayload generate(Long projectId) {
        projectService.get(projectId);
        String text = inputService.mergedText(projectId);
        ResearchProfilePayload payload;
        if (chatService.isConfigured()) {
            try { payload = generateByLlm(text); }
            catch (Exception e) { payload = fallback(text); }
        } else payload = fallback(text);
        ResearchProfile entity = repository.findByProjectId(projectId).orElseGet(ResearchProfile::new);
        entity.setProjectId(projectId);
        apply(entity, payload, false);
        repository.save(entity);
        projectService.updateStatus(projectId, ProjectStatus.PROFILE_GENERATED);
        return toPayload(entity);
    }

    @Transactional
    public ResearchProfilePayload save(Long projectId, ResearchProfilePayload payload) {
        projectService.get(projectId);
        ResearchProfile entity = repository.findByProjectId(projectId).orElseGet(ResearchProfile::new);
        entity.setProjectId(projectId);
        apply(entity, normalizePayload(payload), payload.confirmed());
        repository.save(entity);
        projectService.updateStatus(projectId,
                payload.confirmed() ? ProjectStatus.PROFILE_CONFIRMED : ProjectStatus.PROFILE_GENERATED);
        return toPayload(entity);
    }

    private ResearchProfilePayload generateByLlm(String text) throws Exception {
        String system = "你是科研检索助手。请把用户的实验记录或研究想法整理成严格 JSON，不要添加 Markdown。";
        String prompt = """
                请从下面输入中提取研究卡片，严格返回一个 JSON 对象，字段必须是：
                domain, researchObject, researchProblem, proposedMethod,
                variables, keywordsZh, keywordsEn, coreKeywords, methodKeywords,
                expandedKeywords, excludedKeywords, keywordGroups, relatedFields, researchQuestions。

                除前四个字段外，其余字段均为数组；keywordGroups 必须是二维字符串数组。
                要求：
                1. coreKeywords 表示研究对象、核心问题和必须重点匹配的英文术语；
                2. methodKeywords 表示算法、模型、实验方法和技术路线；
                3. expandedKeywords 表示邻近领域、同义表达和可迁移方法；
                4. excludedKeywords 只填写用户明确排除的方向，不要自行猜测；
                5. keywordGroups 每组至少两个词，表示必须联合考虑的概念；
                6. 保留必要缩写和全称，例如 DPC 与 differential phase contrast、PTF 与 phase transfer function；
                7. keywordsEn 为前三类英文关键词的去重合集，用于兼容旧接口；
                8. 不确定内容写空字符串或空数组，不要虚构实验结果。

                用户输入：
                %s
                """.formatted(limit(text, 14000));
        String raw = chatService.ask(system, prompt);
        JsonNode node = objectMapper.readTree(JsonUtils.extractJsonObject(raw));
        List<String> core = JsonUtils.stringArray(node, "coreKeywords");
        List<String> methods = JsonUtils.stringArray(node, "methodKeywords");
        List<String> expanded = JsonUtils.stringArray(node, "expandedKeywords");
        List<String> legacy = JsonUtils.stringArray(node, "keywordsEn");
        List<String> union = union(legacy, core, methods, expanded);
        return new ResearchProfilePayload(
                JsonUtils.text(node, "domain"), JsonUtils.text(node, "researchObject"),
                JsonUtils.text(node, "researchProblem"), JsonUtils.text(node, "proposedMethod"),
                JsonUtils.stringArray(node, "variables"), JsonUtils.stringArray(node, "keywordsZh"), union,
                core.isEmpty() ? union.stream().limit(4).toList() : core,
                methods, expanded, JsonUtils.stringArray(node, "excludedKeywords"),
                JsonUtils.nestedStringArray(node, "keywordGroups"),
                JsonUtils.stringArray(node, "relatedFields"), JsonUtils.stringArray(node, "researchQuestions"), false);
    }

    private ResearchProfilePayload fallback(String text) {
        String compact = text.replaceAll("\\s+", " ").trim();
        String lower = compact.toLowerCase(Locale.ROOT);
        List<String> core = new ArrayList<>();
        List<String> methods = new ArrayList<>();
        List<String> expanded = new ArrayList<>();
        List<String> zh = new ArrayList<>();
        List<List<String>> groups = new ArrayList<>();
        if (lower.contains("dpc") || compact.contains("差分相衬")) {
            core.add("differential phase contrast"); core.add("DPC"); zh.add("差分相衬显微成像");
        }
        if (lower.contains("ptf") || compact.contains("相位传递函数")) {
            core.add("phase transfer function"); core.add("PTF");
            methods.add("blind transfer function estimation"); zh.add("相位传递函数校准");
            groups.add(List.of("differential phase contrast", "phase transfer function"));
        }
        if (lower.contains("正则")) { methods.add("regularized phase reconstruction"); zh.add("相位重建正则化"); }
        if (lower.contains("失配") || lower.contains("mismatch")) expanded.add("model mismatch");
        if (lower.contains("calibration") || compact.contains("校准")) methods.add("self-calibration");
        if (core.isEmpty()) core.addAll(extractEnglishPhrases(compact).stream().limit(5).toList());
        if (core.isEmpty()) core.add("scientific research");
        String problem = compact.length() > 1000 ? compact.substring(0, 1000) : compact;
        List<String> all = union(core, methods, expanded);
        return new ResearchProfilePayload(
                detectDomain(lower), "用户实验或研究方案", problem, problem, new ArrayList<>(), zh, all,
                core, methods, expanded, List.of(), groups,
                List.of("related methods", "adjacent research fields"),
                List.of("What prior work addresses the same research problem?",
                        "Which methods are most similar to the proposed approach?",
                        "What limitations remain in existing work?"), false);
    }

    private ResearchProfilePayload normalizePayload(ResearchProfilePayload p) {
        List<String> core = clean(p.coreKeywords());
        List<String> methods = clean(p.methodKeywords());
        List<String> expanded = clean(p.expandedKeywords());
        List<String> legacy = clean(p.keywordsEn());
        if (core.isEmpty() && methods.isEmpty() && expanded.isEmpty()) core = legacy;
        return new ResearchProfilePayload(nvl(p.domain()), nvl(p.researchObject()), nvl(p.researchProblem()), nvl(p.proposedMethod()),
                clean(p.variables()), clean(p.keywordsZh()), union(legacy, core, methods, expanded), core, methods, expanded,
                clean(p.excludedKeywords()), cleanGroups(p.keywordGroups()), clean(p.relatedFields()), clean(p.researchQuestions()), p.confirmed());
    }

    private String detectDomain(String lower) {
        if (lower.contains("dpc") || lower.contains("phase") || lower.contains("显微")) return "Computational Imaging";
        if (lower.contains("navigation") || lower.contains("导航")) return "Robot Navigation";
        if (lower.contains("rag") || lower.contains("检索增强")) return "Retrieval-Augmented Generation";
        return "Scientific Research";
    }

    private List<String> extractEnglishPhrases(String text) {
        List<String> values = new ArrayList<>();
        for (String token : text.split("[^A-Za-z0-9+-]+")) {
            if (token.length() >= 3) values.add(token);
            if (values.size() >= 8) break;
        }
        return values;
    }

    private void apply(ResearchProfile entity, ResearchProfilePayload p, boolean confirmed) {
        try {
            ResearchProfilePayload payload = normalizePayload(p);
            entity.setDomain(payload.domain()); entity.setResearchObject(payload.researchObject());
            entity.setResearchProblem(payload.researchProblem()); entity.setProposedMethod(payload.proposedMethod());
            entity.setVariablesJson(objectMapper.writeValueAsString(payload.variables()));
            entity.setKeywordsZhJson(objectMapper.writeValueAsString(payload.keywordsZh()));
            entity.setKeywordsEnJson(objectMapper.writeValueAsString(payload.keywordsEn()));
            entity.setCoreKeywordsJson(objectMapper.writeValueAsString(payload.coreKeywords()));
            entity.setMethodKeywordsJson(objectMapper.writeValueAsString(payload.methodKeywords()));
            entity.setExpandedKeywordsJson(objectMapper.writeValueAsString(payload.expandedKeywords()));
            entity.setExcludedKeywordsJson(objectMapper.writeValueAsString(payload.excludedKeywords()));
            entity.setKeywordGroupsJson(objectMapper.writeValueAsString(payload.keywordGroups()));
            entity.setRelatedFieldsJson(objectMapper.writeValueAsString(payload.relatedFields()));
            entity.setResearchQuestionsJson(objectMapper.writeValueAsString(payload.researchQuestions()));
            entity.setConfirmed(confirmed);
        } catch (Exception e) { throw new IllegalStateException("保存研究卡片失败", e); }
    }

    private ResearchProfilePayload toPayload(ResearchProfile e) {
        List<String> legacy = JsonUtils.readStringList(objectMapper, e.getKeywordsEnJson());
        List<String> core = JsonUtils.readStringList(objectMapper, e.getCoreKeywordsJson());
        List<String> methods = JsonUtils.readStringList(objectMapper, e.getMethodKeywordsJson());
        List<String> expanded = JsonUtils.readStringList(objectMapper, e.getExpandedKeywordsJson());
        if (core.isEmpty() && methods.isEmpty() && expanded.isEmpty()) core = legacy;
        return new ResearchProfilePayload(e.getDomain(), e.getResearchObject(), e.getResearchProblem(), e.getProposedMethod(),
                JsonUtils.readStringList(objectMapper, e.getVariablesJson()), JsonUtils.readStringList(objectMapper, e.getKeywordsZhJson()),
                union(legacy, core, methods, expanded), core, methods, expanded,
                JsonUtils.readStringList(objectMapper, e.getExcludedKeywordsJson()),
                JsonUtils.readNestedStringList(objectMapper, e.getKeywordGroupsJson()),
                JsonUtils.readStringList(objectMapper, e.getRelatedFieldsJson()),
                JsonUtils.readStringList(objectMapper, e.getResearchQuestionsJson()), e.isConfirmed());
    }

    @SafeVarargs
    private final List<String> union(List<String>... lists) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (List<String> list : lists) if (list != null) for (String v : list) if (v != null && !v.isBlank()) out.add(v.trim());
        return new ArrayList<>(out);
    }
    private List<String> clean(List<String> values) { return union(values); }
    private List<List<String>> cleanGroups(List<List<String>> groups) {
        if (groups == null) return List.of();
        List<List<String>> out = new ArrayList<>();
        for (List<String> group : groups) { List<String> clean = clean(group); if (clean.size() >= 2) out.add(clean); }
        return out;
    }
    private String nvl(String value) { return value == null ? "" : value.trim(); }
    private String limit(String value, int max) { return value.length() <= max ? value : value.substring(0, max); }
}
