package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "research_profile")
public class ResearchProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long projectId;

    @Column(length = 300)
    private String domain;

    @Column(length = 1000)
    private String researchObject;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String researchProblem;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String proposedMethod;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String variablesJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String keywordsZhJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String keywordsEnJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String coreKeywordsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String methodKeywordsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String expandedKeywordsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String excludedKeywordsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String keywordGroupsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String relatedFieldsJson;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String researchQuestionsJson;

    @Column(nullable = false)
    private boolean confirmed = false;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getDomain() { return domain; }
    public void setDomain(String domain) { this.domain = domain; }
    public String getResearchObject() { return researchObject; }
    public void setResearchObject(String researchObject) { this.researchObject = researchObject; }
    public String getResearchProblem() { return researchProblem; }
    public void setResearchProblem(String researchProblem) { this.researchProblem = researchProblem; }
    public String getProposedMethod() { return proposedMethod; }
    public void setProposedMethod(String proposedMethod) { this.proposedMethod = proposedMethod; }
    public String getVariablesJson() { return variablesJson; }
    public void setVariablesJson(String variablesJson) { this.variablesJson = variablesJson; }
    public String getKeywordsZhJson() { return keywordsZhJson; }
    public void setKeywordsZhJson(String keywordsZhJson) { this.keywordsZhJson = keywordsZhJson; }
    public String getKeywordsEnJson() { return keywordsEnJson; }
    public void setKeywordsEnJson(String keywordsEnJson) { this.keywordsEnJson = keywordsEnJson; }
    public String getCoreKeywordsJson() { return coreKeywordsJson; }
    public void setCoreKeywordsJson(String coreKeywordsJson) { this.coreKeywordsJson = coreKeywordsJson; }
    public String getMethodKeywordsJson() { return methodKeywordsJson; }
    public void setMethodKeywordsJson(String methodKeywordsJson) { this.methodKeywordsJson = methodKeywordsJson; }
    public String getExpandedKeywordsJson() { return expandedKeywordsJson; }
    public void setExpandedKeywordsJson(String expandedKeywordsJson) { this.expandedKeywordsJson = expandedKeywordsJson; }
    public String getExcludedKeywordsJson() { return excludedKeywordsJson; }
    public void setExcludedKeywordsJson(String excludedKeywordsJson) { this.excludedKeywordsJson = excludedKeywordsJson; }
    public String getKeywordGroupsJson() { return keywordGroupsJson; }
    public void setKeywordGroupsJson(String keywordGroupsJson) { this.keywordGroupsJson = keywordGroupsJson; }
    public String getRelatedFieldsJson() { return relatedFieldsJson; }
    public void setRelatedFieldsJson(String relatedFieldsJson) { this.relatedFieldsJson = relatedFieldsJson; }
    public String getResearchQuestionsJson() { return researchQuestionsJson; }
    public void setResearchQuestionsJson(String researchQuestionsJson) { this.researchQuestionsJson = researchQuestionsJson; }
    public boolean isConfirmed() { return confirmed; }
    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }
}
