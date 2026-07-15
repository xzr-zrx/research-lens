package org.example.entity;

import jakarta.persistence.*;
import org.example.enums.EvidenceLevel;

@Entity
@Table(name = "evidence")
public class Evidence {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectPaperId;

    @Column(length = 300)
    private String section;

    private Integer pageNumber;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private EvidenceLevel evidenceLevel;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectPaperId() { return projectPaperId; }
    public void setProjectPaperId(Long projectPaperId) { this.projectPaperId = projectPaperId; }
    public String getSection() { return section; }
    public void setSection(String section) { this.section = section; }
    public Integer getPageNumber() { return pageNumber; }
    public void setPageNumber(Integer pageNumber) { this.pageNumber = pageNumber; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public EvidenceLevel getEvidenceLevel() { return evidenceLevel; }
    public void setEvidenceLevel(EvidenceLevel evidenceLevel) { this.evidenceLevel = evidenceLevel; }
}
