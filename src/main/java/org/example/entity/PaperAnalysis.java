package org.example.entity;

import jakarta.persistence.*;
import org.example.enums.EvidenceLevel;
import org.example.enums.RelationType;

@Entity
@Table(name = "paper_analysis")
public class PaperAnalysis {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectPaperId;

    @Lob @Column(columnDefinition = "CLOB")
    private String researchProblem;
    @Lob @Column(columnDefinition = "CLOB")
    private String method;
    @Lob @Column(columnDefinition = "CLOB")
    private String researchObject;
    @Lob @Column(columnDefinition = "CLOB")
    private String inputOrExperiment;
    @Lob @Column(columnDefinition = "CLOB")
    private String mainResults;
    @Lob @Column(columnDefinition = "CLOB")
    private String limitations;
    @Lob @Column(columnDefinition = "CLOB")
    private String relationToUserIdea;

    @Enumerated(EnumType.STRING)
    @Column(length = 60)
    private RelationType relationType;

    private Double confidence;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EvidenceLevel evidenceLevel;

    @Lob @Column(columnDefinition = "CLOB")
    private String analysisJson;

    @Lob @Column(columnDefinition = "CLOB")
    private String failureReason;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectPaperId() { return projectPaperId; }
    public void setProjectPaperId(Long projectPaperId) { this.projectPaperId = projectPaperId; }
    public String getResearchProblem() { return researchProblem; }
    public void setResearchProblem(String researchProblem) { this.researchProblem = researchProblem; }
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getResearchObject() { return researchObject; }
    public void setResearchObject(String researchObject) { this.researchObject = researchObject; }
    public String getInputOrExperiment() { return inputOrExperiment; }
    public void setInputOrExperiment(String inputOrExperiment) { this.inputOrExperiment = inputOrExperiment; }
    public String getMainResults() { return mainResults; }
    public void setMainResults(String mainResults) { this.mainResults = mainResults; }
    public String getLimitations() { return limitations; }
    public void setLimitations(String limitations) { this.limitations = limitations; }
    public String getRelationToUserIdea() { return relationToUserIdea; }
    public void setRelationToUserIdea(String relationToUserIdea) { this.relationToUserIdea = relationToUserIdea; }
    public RelationType getRelationType() { return relationType; }
    public void setRelationType(RelationType relationType) { this.relationType = relationType; }
    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }
    public EvidenceLevel getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(EvidenceLevel evidenceLevel) { this.evidenceLevel = evidenceLevel; }
    public String getAnalysisJson() { return analysisJson; }
    public void setAnalysisJson(String analysisJson) { this.analysisJson = analysisJson; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}
