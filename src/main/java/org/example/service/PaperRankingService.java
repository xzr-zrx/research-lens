package org.example.service;

import org.example.config.AcademicRankingProperties;
import org.example.dto.PaperCandidate;
import org.example.dto.RankedPaperCandidate;
import org.example.dto.ResearchProfilePayload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Year;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PaperRankingService {
    private final VectorEmbeddingService embeddingService;
    private final AcademicRankingProperties properties;

    @Value("${academic.search.max-candidates:50}")
    private int maxCandidates;

    public PaperRankingService(VectorEmbeddingService embeddingService, AcademicRankingProperties properties) {
        this.embeddingService = embeddingService;
        this.properties = properties;
    }

    public List<RankedPaperCandidate> rank(ResearchProfilePayload profile, List<PaperCandidate> papers) {
        return rank(profile, papers, null, null);
    }

    public List<RankedPaperCandidate> rank(ResearchProfilePayload profile, List<PaperCandidate> papers,
                                           Integer startYear, Integer endYear) {
        List<PaperCandidate> candidates = papers.stream()
                .sorted(Comparator.comparing((PaperCandidate p) -> isBlank(p.getAbstractText()))
                        .thenComparing(p -> Optional.ofNullable(p.getCitationCount()).orElse(0), Comparator.reverseOrder()))
                .limit(maxCandidates)
                .toList();
        if (candidates.isEmpty()) return List.of();

        KeywordSet keywords = KeywordSet.from(profile);
        String queryText = buildQueryText(profile, keywords);
        List<Double> semanticScores = embeddingScores(queryText, candidates);
        int maxCitations = candidates.stream().map(PaperCandidate::getCitationCount).filter(Objects::nonNull)
                .max(Integer::compareTo).orElse(0);

        List<RankedPaperCandidate> ranked = new ArrayList<>();
        for (int i = 0; i < candidates.size(); i++) {
            PaperCandidate paper = candidates.get(i);
            String title = safe(paper.getTitle());
            String abstractText = safe(paper.getAbstractText());
            double semantic = clamp(semanticScores.get(i));

            List<String> matchedCore = matched(keywords.core(), title + " " + abstractText);
            List<String> matchedMethod = matched(keywords.methods(), title + " " + abstractText);
            List<List<String>> matchedGroups = matchedGroups(keywords.groups(), title, abstractText);

            int titleCoreHits = matched(keywords.core(), title).size();
            int abstractCoreHits = matched(keywords.core(), abstractText).size();
            int totalCoreHits = matchedCore.size();
            int totalMethodHits = matchedMethod.size();

            if (properties.getFiltering().isExcludedTitleFilter() && anyMatch(keywords.excluded(), title)) continue;
            boolean keywordQualified = titleCoreHits >= properties.getFiltering().getMinimumCoreTitleHit()
                    || abstractCoreHits >= properties.getFiltering().getMinimumCoreAbstractHit()
                    || (totalCoreHits >= 1 && totalMethodHits >= 1)
                    || keywords.core().isEmpty();
            if (!keywordQualified && semantic < properties.getFiltering().getSemanticFallbackThreshold()) continue;

            double coreCoverage = weightedCoverage(keywords.core(), matchedCore,
                    properties.getKeyword().getCoreKeywordWeight());
            double titleMatch = titleMatchScore(keywords, title);
            double phraseGroup = phraseGroupScore(keywords.groups(), title, abstractText);
            double abstractMatch = saturatedAbstractScore(keywords, abstractText);
            double keywordScore = clamp(coreCoverage * properties.getKeyword().getCoreCoverageWeight()
                    + titleMatch * properties.getKeyword().getTitleMatchWeight()
                    + phraseGroup * properties.getKeyword().getPhraseGroupWeight()
                    + abstractMatch * properties.getKeyword().getAbstractMatchWeight());

            if (anyMatch(keywords.excluded(), abstractText)) {
                keywordScore = clamp(keywordScore * (1.0 - properties.getFiltering().getExcludedAbstractPenalty()));
            }

            double qualityScore = qualityScore(paper, startYear, endYear, maxCitations);
            double finalScore = clamp(keywordScore * properties.getKeywordWeight()
                    + semantic * properties.getSemanticWeight()
                    + qualityScore * properties.getQualityWeight());
            if (isBlank(paper.getAbstractText())) finalScore = clamp(finalScore * 0.88);
            if (finalScore < properties.getMinimumFinalScore()) continue;

            ranked.add(new RankedPaperCandidate(paper, semantic, keywordScore, titleMatch, qualityScore,
                    coreCoverage, phraseGroup, abstractMatch, finalScore, matchedCore, matchedMethod, matchedGroups,
                    recommendationReason(titleCoreHits, matchedCore, matchedMethod, matchedGroups, paper)));
        }
        return ranked.stream().sorted(Comparator.comparingDouble(RankedPaperCandidate::finalScore).reversed()).toList();
    }

    private double titleMatchScore(KeywordSet set, String title) {
        double possible = set.core().size() * 1.0 + set.methods().size() * 0.6 + set.expanded().size() * 0.3;
        if (possible <= 0) return 0;
        double hit = matched(set.core(), title).size() * 1.0
                + matched(set.methods(), title).size() * 0.6
                + matched(set.expanded(), title).size() * 0.3;
        return clamp(hit / possible);
    }

    private double saturatedAbstractScore(KeywordSet set, String abstractText) {
        if (abstractText.isBlank()) return 0;
        List<WeightedKeyword> all = new ArrayList<>();
        set.core().forEach(k -> all.add(new WeightedKeyword(k, properties.getKeyword().getCoreKeywordWeight())));
        set.methods().forEach(k -> all.add(new WeightedKeyword(k, properties.getKeyword().getMethodKeywordWeight())));
        set.expanded().forEach(k -> all.add(new WeightedKeyword(k, properties.getKeyword().getExpandedKeywordWeight())));
        if (all.isEmpty()) return 0;
        int length = Math.max(1, tokenize(abstractText).size());
        double norm = 0.25 + 0.75 * (length / 250.0);
        double score = 0, max = 0;
        for (WeightedKeyword item : all) {
            int tf = occurrenceCount(abstractText, item.keyword());
            double saturated = tf <= 0 ? 0 : tf / (tf + 1.2 * norm);
            score += item.weight() * saturated;
            max += item.weight() * (1.0 / (1.0 + 1.2 * 0.25));
        }
        return max <= 0 ? 0 : clamp(score / max);
    }

    private double phraseGroupScore(List<List<String>> groups, String title, String abstractText) {
        if (groups.isEmpty()) return 0;
        double total = 0;
        for (List<String> group : groups) total += groupLocationScore(group, title, abstractText);
        return clamp(total / groups.size());
    }

    private double groupLocationScore(List<String> group, String title, String abstractText) {
        if (group == null || group.size() < 2) return 0;
        if (group.stream().allMatch(k -> containsKeyword(title, k))) return 1.0;
        List<String> sentences = splitSentences(abstractText);
        for (String sentence : sentences) if (group.stream().allMatch(k -> containsKeyword(sentence, k))) return 0.8;
        for (int i = 0; i + 1 < sentences.size(); i++) {
            String adjacent = sentences.get(i) + " " + sentences.get(i + 1);
            if (group.stream().allMatch(k -> containsKeyword(adjacent, k))) return 0.6;
        }
        return group.stream().allMatch(k -> containsKeyword(abstractText, k)) ? 0.3 : 0;
    }

    private double qualityScore(PaperCandidate paper, Integer startYear, Integer endYear, int maxCitations) {
        double recency;
        Integer year = paper.getPublicationYear();
        if (startYear != null || endYear != null) {
            recency = year == null ? 0.3 : 1.0; // 用户已指定范围，范围内论文不再因新旧被额外歧视。
        } else if (year == null) recency = 0.3;
        else recency = Math.exp(-0.08 * Math.max(0, Year.now().getValue() - year));
        double citation = maxCitations <= 0 || paper.getCitationCount() == null ? 0
                : Math.log1p(Math.max(0, paper.getCitationCount())) / Math.log1p(maxCitations);
        double fulltext = !isBlank(paper.getPdfUrl()) || !isBlank(paper.getArxivId()) ? 1.0
                : (!isBlank(paper.getLandingUrl()) || !isBlank(paper.getDoi()) ? 0.4 : 0.0);
        int fields = 0;
        if (!isBlank(paper.getTitle())) fields++;
        if (!isBlank(paper.getAbstractText())) fields++;
        if (paper.getAuthors() != null && !paper.getAuthors().isEmpty()) fields++;
        if (paper.getPublicationYear() != null) fields++;
        if (!isBlank(paper.getVenue())) fields++;
        if (!isBlank(paper.getDoi()) || !isBlank(paper.getArxivId())) fields++;
        double metadata = fields / 6.0;
        return clamp(recency * properties.getQuality().getRecencyWeight()
                + citation * properties.getQuality().getCitationWeight()
                + fulltext * properties.getQuality().getFulltextAvailabilityWeight()
                + metadata * properties.getQuality().getMetadataCompletenessWeight());
    }

    private List<Double> embeddingScores(String query, List<PaperCandidate> papers) {
        List<Double> scores = new ArrayList<>(Collections.nCopies(papers.size(), 0.0));
        if (!embeddingService.isConfigured() || papers.isEmpty()) {
            for (int i = 0; i < papers.size(); i++)
                scores.set(i, lexicalSimilarity(query, papers.get(i).getTitle() + " " + safe(papers.get(i).getAbstractText())));
            return scores;
        }
        try {
            List<Float> queryVector = embeddingService.generateEmbedding(query);
            for (int start = 0; start < papers.size(); start += 10) {
                int end = Math.min(start + 10, papers.size());
                List<String> texts = papers.subList(start, end).stream()
                        .map(p -> p.getTitle() + "\n" + safe(p.getAbstractText())).toList();
                List<List<Float>> vectors = embeddingService.generateEmbeddings(texts);
                for (int j = 0; j < vectors.size(); j++)
                    scores.set(start + j, (double) Math.max(0, embeddingService.calculateCosineSimilarity(queryVector, vectors.get(j))));
            }
        } catch (Exception e) {
            for (int i = 0; i < papers.size(); i++)
                scores.set(i, lexicalSimilarity(query, papers.get(i).getTitle() + " " + safe(papers.get(i).getAbstractText())));
        }
        return scores;
    }

    private String buildQueryText(ResearchProfilePayload profile, KeywordSet set) {
        return String.join(" ", List.of(safe(profile.researchProblem()), safe(profile.proposedMethod()),
                String.join(" ", set.core()), String.join(" ", set.methods()),
                String.join(" ", safeList(profile.researchQuestions()))));
    }

    private String recommendationReason(int titleCoreHits, List<String> cores, List<String> methods,
                                        List<List<String>> groups, PaperCandidate paper) {
        List<String> reasons = new ArrayList<>();
        if (titleCoreHits > 0) reasons.add("标题命中 " + titleCoreHits + " 个核心术语");
        if (!groups.isEmpty()) reasons.add("命中关键词组合“" + String.join(" + ", groups.get(0)) + "”");
        if (!methods.isEmpty()) reasons.add("方法词匹配：" + String.join("、", methods.stream().limit(3).toList()));
        if (!isBlank(paper.getPdfUrl())) reasons.add("存在可自动获取的开放全文");
        if (reasons.isEmpty() && !cores.isEmpty()) reasons.add("核心词匹配：" + String.join("、", cores.stream().limit(3).toList()));
        return reasons.isEmpty() ? "语义与研究问题具有较高相关性" : String.join("；", reasons) + "。";
    }

    private List<String> matched(List<String> keywords, String text) {
        return keywords.stream().filter(k -> containsKeyword(text, k)).distinct().toList();
    }
    private List<List<String>> matchedGroups(List<List<String>> groups, String title, String abstractText) {
        return groups.stream().filter(g -> groupLocationScore(g, title, abstractText) > 0).toList();
    }
    private boolean anyMatch(List<String> keywords, String text) { return keywords.stream().anyMatch(k -> containsKeyword(text, k)); }
    private double weightedCoverage(List<String> all, List<String> hit, double weight) {
        if (all.isEmpty()) return 0;
        return clamp(hit.size() * weight / (all.size() * weight));
    }
    private boolean containsKeyword(String text, String keyword) {
        if (isBlank(text) || isBlank(keyword)) return false;
        String normalizedText = normalize(text);
        String normalizedKeyword = normalize(keyword);
        if (normalizedKeyword.isBlank()) return false;
        return (" " + normalizedText + " ").contains(" " + normalizedKeyword + " ");
    }
    private int occurrenceCount(String text, String keyword) {
        if (isBlank(text) || isBlank(keyword)) return 0;
        String t = " " + normalize(text) + " ";
        String k = " " + normalize(keyword) + " ";
        int count = 0, from = 0;
        while ((from = t.indexOf(k, from)) >= 0) { count++; from += Math.max(1, k.length()); }
        return count;
    }
    private String normalize(String value) {
        return safe(value).toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").replaceAll("\\s+", " ").trim();
    }
    private List<String> splitSentences(String value) {
        if (isBlank(value)) return List.of();
        return Arrays.stream(value.split("(?<=[.!?。！？;；])\\s*"))
                .map(String::trim).filter(s -> !s.isBlank()).toList();
    }
    private double lexicalSimilarity(String a, String b) {
        Set<String> x = tokenize(a), y = tokenize(b);
        if (x.isEmpty() || y.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(x); intersection.retainAll(y);
        Set<String> union = new HashSet<>(x); union.addAll(y);
        return (double) intersection.size() / union.size();
    }
    private Set<String> tokenize(String value) {
        if (value == null) return Set.of();
        return Arrays.stream(normalize(value).split(" ")).filter(t -> t.length() > 2)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    private boolean isBlank(String value) { return value == null || value.isBlank(); }
    private String safe(String value) { return value == null ? "" : value; }
    private List<String> safeList(List<String> values) { return values == null ? List.of() : values; }
    private double clamp(double value) { return Math.max(0, Math.min(1, value)); }

    private record WeightedKeyword(String keyword, double weight) {}
    private record KeywordSet(List<String> core, List<String> methods, List<String> expanded,
                              List<String> excluded, List<List<String>> groups) {
        static KeywordSet from(ResearchProfilePayload profile) {
            List<String> core = clean(profile.coreKeywords());
            List<String> methods = clean(profile.methodKeywords());
            List<String> expanded = clean(profile.expandedKeywords());
            if (core.isEmpty() && methods.isEmpty() && expanded.isEmpty()) core = clean(profile.keywordsEn());
            return new KeywordSet(core, methods, expanded, clean(profile.excludedKeywords()), cleanGroups(profile.keywordGroups()));
        }
        private static List<String> clean(List<String> input) {
            if (input == null) return List.of();
            LinkedHashMap<String, String> values = new LinkedHashMap<>();
            for (String value : input) if (value != null && !value.isBlank()) values.putIfAbsent(value.trim().toLowerCase(Locale.ROOT), value.trim());
            return new ArrayList<>(values.values());
        }
        private static List<List<String>> cleanGroups(List<List<String>> groups) {
            if (groups == null) return List.of();
            return groups.stream().map(KeywordSet::clean).filter(g -> g.size() >= 2).toList();
        }
    }
}
