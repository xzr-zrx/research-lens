package org.example.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "paper")
public class Paper {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200)
    private String openalexId;

    @Column(length = 100)
    private String arxivId;

    @Column(length = 300)
    private String doi;

    @Column(nullable = false, length = 2000)
    private String title;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String abstractText;

    @Lob
    @Column(columnDefinition = "CLOB")
    private String authorsJson;

    private Integer publicationYear;

    @Column(length = 1000)
    private String venue;

    private Integer citationCount;

    @Column(length = 2000)
    private String landingUrl;

    @Column(length = 2000)
    private String pdfUrl;

    @Column(length = 200)
    private String source;

    @Column(length = 1000)
    private String localFullTextPath;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOpenalexId() { return openalexId; }
    public void setOpenalexId(String openalexId) { this.openalexId = openalexId; }
    public String getArxivId() { return arxivId; }
    public void setArxivId(String arxivId) { this.arxivId = arxivId; }
    public String getDoi() { return doi; }
    public void setDoi(String doi) { this.doi = doi; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAbstractText() { return abstractText; }
    public void setAbstractText(String abstractText) { this.abstractText = abstractText; }
    public String getAuthorsJson() { return authorsJson; }
    public void setAuthorsJson(String authorsJson) { this.authorsJson = authorsJson; }
    public Integer getPublicationYear() { return publicationYear; }
    public void setPublicationYear(Integer publicationYear) { this.publicationYear = publicationYear; }
    public String getVenue() { return venue; }
    public void setVenue(String venue) { this.venue = venue; }
    public Integer getCitationCount() { return citationCount; }
    public void setCitationCount(Integer citationCount) { this.citationCount = citationCount; }
    public String getLandingUrl() { return landingUrl; }
    public void setLandingUrl(String landingUrl) { this.landingUrl = landingUrl; }
    public String getPdfUrl() { return pdfUrl; }
    public void setPdfUrl(String pdfUrl) { this.pdfUrl = pdfUrl; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getLocalFullTextPath() { return localFullTextPath; }
    public void setLocalFullTextPath(String localFullTextPath) { this.localFullTextPath = localFullTextPath; }
}
