package org.example.academic;

import org.example.dto.PaperCandidate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class ArxivClient {
    private final RestClient restClient = RestClient.create();
    private long lastRequestAt = 0L;

    @Value("${academic.arxiv.base-url:https://export.arxiv.org/api/query}")
    private String baseUrl;

    @Value("${academic.arxiv.max-results-per-query:10}")
    private int maxResults;

    @Value("${academic.arxiv.request-interval-ms:3000}")
    private long requestIntervalMs;

    /**
     * 按查询和时间范围搜索 arXiv 论文。
     *
     * @param query     检索词
     * @param startYear 开始年份（含），null 表示不限
     * @param endYear   结束年份（含），null 表示不限
     */
    public synchronized List<PaperCandidate> search(String query, Integer startYear, Integer endYear) {
        waitForRateLimit();
        try {
            URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .queryParam("search_query", toArxivQuery(query))
                    .queryParam("start", 0)
                    .queryParam("max_results", maxResults)
                    .queryParam("sortBy", "relevance")
                    .build().encode().toUri();
            String xml = restClient.get().uri(uri).retrieve().body(String.class);
            lastRequestAt = System.currentTimeMillis();
            List<PaperCandidate> all = parse(xml == null ? "" : xml);
            // 后端校验：过滤超出时间范围的论文
            return filterByYear(all, startYear, endYear);
        } catch (Exception e) {
            lastRequestAt = System.currentTimeMillis();
            throw new IllegalStateException("arXiv 检索失败: " + e.getMessage(), e);
        }
    }

    private List<PaperCandidate> filterByYear(List<PaperCandidate> papers, Integer startYear, Integer endYear) {
        if (startYear == null && endYear == null) return papers;
        return papers.stream().filter(p -> {
            Integer year = p.getPublicationYear();
            if (year == null) return false; // 未知年份的论文排除
            boolean afterStart = startYear == null || year >= startYear;
            boolean beforeEnd = endYear == null || year <= endYear;
            return afterStart && beforeEnd;
        }).toList();
    }

    public List<PaperCandidate> parse(String xml) throws Exception {
        if (xml == null || xml.isBlank()) return List.of();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        Document document = factory.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList entries = document.getElementsByTagNameNS("*", "entry");
        List<PaperCandidate> papers = new ArrayList<>();
        for (int i = 0; i < entries.getLength(); i++) {
            Element entry = (Element) entries.item(i);
            PaperCandidate paper = new PaperCandidate();
            String idUrl = text(entry, "id");
            paper.setArxivId(extractArxivId(idUrl));
            paper.setTitle(clean(text(entry, "title")));
            paper.setAbstractText(clean(text(entry, "summary")));
            String published = text(entry, "published");
            try {
                paper.setPublicationYear(OffsetDateTime.parse(published).getYear());
            } catch (Exception ignored) {
                paper.setPublicationYear(null);
            }
            List<String> authors = new ArrayList<>();
            NodeList authorNodes = entry.getElementsByTagNameNS("*", "author");
            for (int j = 0; j < authorNodes.getLength(); j++) {
                Element author = (Element) authorNodes.item(j);
                String name = text(author, "name");
                if (!name.isBlank()) authors.add(name);
            }
            paper.setAuthors(authors);
            paper.setLandingUrl(idUrl);
            NodeList links = entry.getElementsByTagNameNS("*", "link");
            for (int j = 0; j < links.getLength(); j++) {
                Element link = (Element) links.item(j);
                if ("application/pdf".equalsIgnoreCase(link.getAttribute("type"))) {
                    paper.setPdfUrl(link.getAttribute("href"));
                }
            }
            NodeList doiNodes = entry.getElementsByTagNameNS("*", "doi");
            if (doiNodes.getLength() > 0) paper.setDoi(clean(doiNodes.item(0).getTextContent()));
            NodeList journalNodes = entry.getElementsByTagNameNS("*", "journal_ref");
            if (journalNodes.getLength() > 0) paper.setVenue(clean(journalNodes.item(0).getTextContent()));
            paper.setSources(new ArrayList<>(List.of("arXiv")));
            if (!paper.getTitle().isBlank()) papers.add(paper);
        }
        return papers;
    }

    private String toArxivQuery(String query) {
        if (query == null || query.isBlank()) return "all:\"scientific research\"";
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("\"([^\"]+)\"|(?i)\\b(AND|OR)\\b|([^\\s]+)")
                .matcher(query.trim());
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.group(1) != null) tokens.add("all:\"" + matcher.group(1).trim() + "\"");
            else if (matcher.group(2) != null) tokens.add(matcher.group(2).toUpperCase());
            else if (matcher.group(3) != null && !matcher.group(3).isBlank()) tokens.add("all:" + matcher.group(3));
        }
        StringBuilder out = new StringBuilder();
        boolean previousTerm = false;
        for (String token : tokens) {
            boolean operator = token.equals("AND") || token.equals("OR");
            if (!operator && previousTerm) out.append(" AND ");
            else if (operator) out.append(' ');
            out.append(token);
            if (operator) out.append(' ');
            previousTerm = !operator;
        }
        return out.toString().replaceAll("\\s+", " ").trim();
    }

    private void waitForRateLimit() {
        long wait = requestIntervalMs - (System.currentTimeMillis() - lastRequestAt);
        if (wait > 0) {
            try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
    }

    private String text(Element parent, String localName) {
        NodeList nodes = parent.getElementsByTagNameNS("*", localName);
        return nodes.getLength() == 0 ? "" : clean(nodes.item(0).getTextContent());
    }

    private String clean(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").trim();
    }

    private String extractArxivId(String url) {
        if (url == null) return "";
        String value = url.substring(url.lastIndexOf('/') + 1);
        return value.replaceFirst("v\\d+$", "");
    }
}
