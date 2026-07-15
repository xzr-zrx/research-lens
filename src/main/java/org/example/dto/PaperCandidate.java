package org.example.dto;

import java.util.ArrayList;
import java.util.List;

public class PaperCandidate {
    private String openalexId;
    private String arxivId;
    private String doi;
    private String title;
    private String abstractText;
    private List<String> authors = new ArrayList<>();
    private Integer publicationYear;
    private String venue;
    private Integer citationCount;
    private String landingUrl;
    private String pdfUrl;
    private List<String> sources = new ArrayList<>();

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
    public List<String> getAuthors() { return authors; }
    public void setAuthors(List<String> authors) { this.authors = authors == null ? new ArrayList<>() : authors; }
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
    public List<String> getSources() { return sources; }
    public void setSources(List<String> sources) { this.sources = sources == null ? new ArrayList<>() : sources; }
}
