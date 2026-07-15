package org.example.entity;

import jakarta.persistence.*;
import org.example.enums.EvidenceLevel;
import org.example.enums.KnowledgeStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "project_paper")
public class ProjectPaper {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false)
    private Long paperId;

    @Column(length = 200)
    private String retrievalSources;

    private Double embeddingScore = 0.0;
    private Double keywordScore = 0.0;
    private Double titleScore = 0.0;
    private Double qualityScore = 0.0;
    private Double coreCoverageScore = 0.0;
    private Double phraseGroupScore = 0.0;
    private Double abstractMatchScore = 0.0;
    private Double finalScore = 0.0;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String matchedCoreKeywordsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String matchedMethodKeywordsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String matchedKeywordGroupsJson;

    @Column(length = 2000)
    private String recommendationReason;
    private Integer rankNumber;
    private boolean selected = false;
    private boolean savedToKnowledge = false;

    @Enumerated(EnumType.STRING)
    @Column(length = 40)
    private KnowledgeStatus knowledgeStatus = KnowledgeStatus.NOT_INDEXED;

    @Column(length = 40)
    private String fullTextSource;

    private LocalDateTime fullTextDownloadedAt;
    private Integer parsedPageCount;
    private Integer sectionCount;
    private Integer chunkCount;
    private Boolean tableDetected = false;

    @Column(length = 100)
    private String failureStage;

    @Column(length = 2000)
    private String failureReason;

    private LocalDateTime knowledgeUpdatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EvidenceLevel evidenceLevel = EvidenceLevel.ABSTRACT;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public Long getPaperId() { return paperId; }
    public void setPaperId(Long paperId) { this.paperId = paperId; }
    public String getRetrievalSources() { return retrievalSources; }
    public void setRetrievalSources(String retrievalSources) { this.retrievalSources = retrievalSources; }
    public Double getEmbeddingScore() { return embeddingScore; }
    public void setEmbeddingScore(Double embeddingScore) { this.embeddingScore = embeddingScore; }
    public Double getKeywordScore() { return keywordScore; }
    public void setKeywordScore(Double keywordScore) { this.keywordScore = keywordScore; }
    public Double getTitleScore() { return titleScore; }
    public void setTitleScore(Double titleScore) { this.titleScore = titleScore; }
    public Double getQualityScore() { return qualityScore; }
    public void setQualityScore(Double qualityScore) { this.qualityScore = qualityScore; }
    public Double getCoreCoverageScore() { return coreCoverageScore; }
    public void setCoreCoverageScore(Double coreCoverageScore) { this.coreCoverageScore = coreCoverageScore; }
    public Double getPhraseGroupScore() { return phraseGroupScore; }
    public void setPhraseGroupScore(Double phraseGroupScore) { this.phraseGroupScore = phraseGroupScore; }
    public Double getAbstractMatchScore() { return abstractMatchScore; }
    public void setAbstractMatchScore(Double abstractMatchScore) { this.abstractMatchScore = abstractMatchScore; }
    public String getMatchedCoreKeywordsJson() { return matchedCoreKeywordsJson; }
    public void setMatchedCoreKeywordsJson(String value) { this.matchedCoreKeywordsJson = value; }
    public String getMatchedMethodKeywordsJson() { return matchedMethodKeywordsJson; }
    public void setMatchedMethodKeywordsJson(String value) { this.matchedMethodKeywordsJson = value; }
    public String getMatchedKeywordGroupsJson() { return matchedKeywordGroupsJson; }
    public void setMatchedKeywordGroupsJson(String value) { this.matchedKeywordGroupsJson = value; }
    public String getRecommendationReason() { return recommendationReason; }
    public void setRecommendationReason(String recommendationReason) { this.recommendationReason = recommendationReason; }
    public Double getFinalScore() { return finalScore; }
    public void setFinalScore(Double finalScore) { this.finalScore = finalScore; }
    public Integer getRankNumber() { return rankNumber; }
    public void setRankNumber(Integer rankNumber) { this.rankNumber = rankNumber; }
    public boolean isSelected() { return selected; }
    public void setSelected(boolean selected) { this.selected = selected; }
    public boolean isSavedToKnowledge() { return savedToKnowledge; }
    public void setSavedToKnowledge(boolean savedToKnowledge) { this.savedToKnowledge = savedToKnowledge; }
    public KnowledgeStatus getKnowledgeStatus() { return knowledgeStatus; }
    public void setKnowledgeStatus(KnowledgeStatus knowledgeStatus) { this.knowledgeStatus = knowledgeStatus; }
    public String getFullTextSource() { return fullTextSource; }
    public void setFullTextSource(String fullTextSource) { this.fullTextSource = fullTextSource; }
    public LocalDateTime getFullTextDownloadedAt() { return fullTextDownloadedAt; }
    public void setFullTextDownloadedAt(LocalDateTime value) { this.fullTextDownloadedAt = value; }
    public Integer getParsedPageCount() { return parsedPageCount; }
    public void setParsedPageCount(Integer value) { this.parsedPageCount = value; }
    public Integer getSectionCount() { return sectionCount; }
    public void setSectionCount(Integer value) { this.sectionCount = value; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer value) { this.chunkCount = value; }
    public Boolean getTableDetected() { return tableDetected; }
    public void setTableDetected(Boolean value) { this.tableDetected = value; }
    public String getFailureStage() { return failureStage; }
    public void setFailureStage(String value) { this.failureStage = value; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String value) { this.failureReason = value; }
    public LocalDateTime getKnowledgeUpdatedAt() { return knowledgeUpdatedAt; }
    public void setKnowledgeUpdatedAt(LocalDateTime value) { this.knowledgeUpdatedAt = value; }
    public EvidenceLevel getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(EvidenceLevel evidenceLevel) { this.evidenceLevel = evidenceLevel; }
}
