package org.example.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "academic.ranking")
public class AcademicRankingProperties {
    private static final Logger log = LoggerFactory.getLogger(AcademicRankingProperties.class);
    private double keywordWeight = 0.60;
    private double semanticWeight = 0.30;
    private double qualityWeight = 0.10;
    private double minimumFinalScore = 0.25;
    private final Keyword keyword = new Keyword();
    private final Quality quality = new Quality();
    private final Filtering filtering = new Filtering();

    @PostConstruct
    public void validate() {
        validateSum("论文总评分", keywordWeight + semanticWeight + qualityWeight);
        validateSum("关键词评分", keyword.coreCoverageWeight + keyword.titleMatchWeight
                + keyword.phraseGroupWeight + keyword.abstractMatchWeight);
        validateSum("质量评分", quality.recencyWeight + quality.citationWeight
                + quality.fulltextAvailabilityWeight + quality.metadataCompletenessWeight);
        if (minimumFinalScore < 0 || minimumFinalScore > 1) {
            throw new IllegalStateException("academic.ranking.minimum-final-score 必须位于 0~1");
        }
        log.info("论文排序权重已加载：关键词={}，语义={}，质量={}", keywordWeight, semanticWeight, qualityWeight);
    }

    private void validateSum(String name, double value) {
        if (Math.abs(value - 1.0) > 0.0001) {
            throw new IllegalStateException(name + "权重之和必须为 1.0，当前为 " + value);
        }
    }

    public double getKeywordWeight() { return keywordWeight; }
    public void setKeywordWeight(double keywordWeight) { this.keywordWeight = keywordWeight; }
    public double getSemanticWeight() { return semanticWeight; }
    public void setSemanticWeight(double semanticWeight) { this.semanticWeight = semanticWeight; }
    public double getQualityWeight() { return qualityWeight; }
    public void setQualityWeight(double qualityWeight) { this.qualityWeight = qualityWeight; }
    public double getMinimumFinalScore() { return minimumFinalScore; }
    public void setMinimumFinalScore(double minimumFinalScore) { this.minimumFinalScore = minimumFinalScore; }
    public Keyword getKeyword() { return keyword; }
    public Quality getQuality() { return quality; }
    public Filtering getFiltering() { return filtering; }

    public static class Keyword {
        private double coreCoverageWeight = 0.40;
        private double titleMatchWeight = 0.25;
        private double phraseGroupWeight = 0.20;
        private double abstractMatchWeight = 0.15;
        private double coreKeywordWeight = 3.0;
        private double methodKeywordWeight = 2.0;
        private double expandedKeywordWeight = 1.0;

        public double getCoreCoverageWeight() { return coreCoverageWeight; }
        public void setCoreCoverageWeight(double value) { this.coreCoverageWeight = value; }
        public double getTitleMatchWeight() { return titleMatchWeight; }
        public void setTitleMatchWeight(double value) { this.titleMatchWeight = value; }
        public double getPhraseGroupWeight() { return phraseGroupWeight; }
        public void setPhraseGroupWeight(double value) { this.phraseGroupWeight = value; }
        public double getAbstractMatchWeight() { return abstractMatchWeight; }
        public void setAbstractMatchWeight(double value) { this.abstractMatchWeight = value; }
        public double getCoreKeywordWeight() { return coreKeywordWeight; }
        public void setCoreKeywordWeight(double value) { this.coreKeywordWeight = value; }
        public double getMethodKeywordWeight() { return methodKeywordWeight; }
        public void setMethodKeywordWeight(double value) { this.methodKeywordWeight = value; }
        public double getExpandedKeywordWeight() { return expandedKeywordWeight; }
        public void setExpandedKeywordWeight(double value) { this.expandedKeywordWeight = value; }
    }

    public static class Quality {
        private double recencyWeight = 0.40;
        private double citationWeight = 0.20;
        private double fulltextAvailabilityWeight = 0.30;
        private double metadataCompletenessWeight = 0.10;

        public double getRecencyWeight() { return recencyWeight; }
        public void setRecencyWeight(double value) { this.recencyWeight = value; }
        public double getCitationWeight() { return citationWeight; }
        public void setCitationWeight(double value) { this.citationWeight = value; }
        public double getFulltextAvailabilityWeight() { return fulltextAvailabilityWeight; }
        public void setFulltextAvailabilityWeight(double value) { this.fulltextAvailabilityWeight = value; }
        public double getMetadataCompletenessWeight() { return metadataCompletenessWeight; }
        public void setMetadataCompletenessWeight(double value) { this.metadataCompletenessWeight = value; }
    }

    public static class Filtering {
        private int minimumCoreTitleHit = 1;
        private int minimumCoreAbstractHit = 2;
        private double semanticFallbackThreshold = 0.82;
        private boolean excludedTitleFilter = true;
        private double excludedAbstractPenalty = 0.20;

        public int getMinimumCoreTitleHit() { return minimumCoreTitleHit; }
        public void setMinimumCoreTitleHit(int value) { this.minimumCoreTitleHit = value; }
        public int getMinimumCoreAbstractHit() { return minimumCoreAbstractHit; }
        public void setMinimumCoreAbstractHit(int value) { this.minimumCoreAbstractHit = value; }
        public double getSemanticFallbackThreshold() { return semanticFallbackThreshold; }
        public void setSemanticFallbackThreshold(double value) { this.semanticFallbackThreshold = value; }
        public boolean isExcludedTitleFilter() { return excludedTitleFilter; }
        public void setExcludedTitleFilter(boolean value) { this.excludedTitleFilter = value; }
        public double getExcludedAbstractPenalty() { return excludedAbstractPenalty; }
        public void setExcludedAbstractPenalty(double value) { this.excludedAbstractPenalty = value; }
    }
}
