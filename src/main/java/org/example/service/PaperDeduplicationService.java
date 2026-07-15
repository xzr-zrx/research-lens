package org.example.service;

import org.example.dto.PaperCandidate;
import org.example.util.TitleNormalizer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PaperDeduplicationService {
    public List<PaperCandidate> deduplicate(List<PaperCandidate> candidates) {
        List<PaperCandidate> result = new ArrayList<>();
        Map<String, PaperCandidate> exact = new LinkedHashMap<>();
        for (PaperCandidate candidate : candidates) {
            if (candidate == null || candidate.getTitle() == null || candidate.getTitle().isBlank()) continue;
            String key = key(candidate);
            PaperCandidate existing = exact.get(key);
            if (existing == null) {
                PaperCandidate similar = findSimilar(result, candidate);
                if (similar != null) merge(similar, candidate);
                else {
                    exact.put(key, candidate);
                    result.add(candidate);
                }
            } else {
                merge(existing, candidate);
            }
        }
        return result;
    }

    private String key(PaperCandidate p) {
        if (p.getDoi() != null && !p.getDoi().isBlank()) return "doi:" + p.getDoi().toLowerCase(Locale.ROOT);
        if (p.getArxivId() != null && !p.getArxivId().isBlank()) return "arxiv:" + p.getArxivId().toLowerCase(Locale.ROOT);
        return "title:" + TitleNormalizer.normalize(p.getTitle());
    }

    private PaperCandidate findSimilar(List<PaperCandidate> papers, PaperCandidate candidate) {
        String firstAuthor = candidate.getAuthors().isEmpty() ? "" : candidate.getAuthors().get(0);
        for (PaperCandidate paper : papers) {
            if (TitleNormalizer.similarity(paper.getTitle(), candidate.getTitle()) < 0.90) continue;
            String otherAuthor = paper.getAuthors().isEmpty() ? "" : paper.getAuthors().get(0);
            boolean authorClose = firstAuthor.isBlank() || otherAuthor.isBlank() || firstAuthor.equalsIgnoreCase(otherAuthor);
            boolean yearClose = paper.getPublicationYear() == null || candidate.getPublicationYear() == null
                    || Math.abs(paper.getPublicationYear() - candidate.getPublicationYear()) <= 1;
            if (authorClose && yearClose) return paper;
        }
        return null;
    }

    private void merge(PaperCandidate target, PaperCandidate source) {
        if (blank(target.getOpenalexId())) target.setOpenalexId(source.getOpenalexId());
        if (blank(target.getArxivId())) target.setArxivId(source.getArxivId());
        if (blank(target.getDoi())) target.setDoi(source.getDoi());
        if (blank(target.getAbstractText()) || length(source.getAbstractText()) > length(target.getAbstractText())) target.setAbstractText(source.getAbstractText());
        if (target.getAuthors().isEmpty()) target.setAuthors(source.getAuthors());
        if (target.getPublicationYear() == null) target.setPublicationYear(source.getPublicationYear());
        if (blank(target.getVenue())) target.setVenue(source.getVenue());
        if (target.getCitationCount() == null) target.setCitationCount(source.getCitationCount());
        if (blank(target.getLandingUrl())) target.setLandingUrl(source.getLandingUrl());
        if (blank(target.getPdfUrl())) target.setPdfUrl(source.getPdfUrl());
        List<String> merged = new ArrayList<>(target.getSources());
        for (String value : source.getSources()) if (!merged.contains(value)) merged.add(value);
        target.setSources(merged);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    private int length(String value) { return value == null ? 0 : value.length(); }
}
