package org.example.academic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.PaperCandidate;
import org.example.util.AbstractInvertedIndexConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Component
public class OpenAlexClient {
    private final ObjectMapper objectMapper;
    private final RestClient restClient = RestClient.create();

    @Value("${academic.openalex.base-url:https://api.openalex.org}")
    private String baseUrl;

    @Value("${academic.openalex.api-key:YOUR_OPENALEX_API_KEY}")
    private String apiKey;

    @Value("${academic.openalex.max-results-per-query:20}")
    private int maxResults;

    public OpenAlexClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 按时间范围搜索论文。
     *
     * @param query   检索式
     * @param startYear 开始年份（含），null 表示不限
     * @param endYear   结束年份（含），null 表示不限
     */
    public List<PaperCandidate> search(String query, Integer startYear, Integer endYear) {
        try {
            UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                    .path("/works")
                    .queryParam("search", normalizeQuery(query))
                    .queryParam("per_page", maxResults)
                    .queryParam("select", "id,doi,title,publication_year,cited_by_count,abstract_inverted_index,authorships,primary_location,best_oa_location,open_access");
            if (apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("YOUR_")) {
                builder.queryParam("api_key", apiKey.trim());
            }
            // OpenAlex 的日期范围必须通过 filter 参数提交。
            List<String> filters = new ArrayList<>();
            if (startYear != null) filters.add("from_publication_date:" + startYear + "-01-01");
            if (endYear != null) filters.add("to_publication_date:" + endYear + "-12-31");
            if (!filters.isEmpty()) builder.queryParam("filter", String.join(",", filters));
            URI uri = builder.build().encode().toUri();
            String body = restClient.get().uri(uri).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body == null ? "{}" : body);
            List<PaperCandidate> papers = new ArrayList<>();
            JsonNode results = root.path("results");
            if (!results.isArray()) return papers;
            for (JsonNode item : results) {
                String title = item.path("title").asText("").trim();
                if (title.isBlank()) continue;
                PaperCandidate paper = new PaperCandidate();
                paper.setOpenalexId(item.path("id").asText(""));
                paper.setDoi(normalizeDoi(item.path("doi").asText("")));
                paper.setTitle(title);
                paper.setAbstractText(AbstractInvertedIndexConverter.convert(item.get("abstract_inverted_index")));
                paper.setPublicationYear(item.path("publication_year").isNumber() ? item.path("publication_year").asInt() : null);
                paper.setCitationCount(item.path("cited_by_count").isNumber() ? item.path("cited_by_count").asInt() : null);

                List<String> authors = new ArrayList<>();
                JsonNode authorships = item.path("authorships");
                if (authorships.isArray()) {
                    authorships.forEach(a -> {
                        String name = a.path("author").path("display_name").asText("").trim();
                        if (!name.isBlank()) authors.add(name);
                    });
                }
                paper.setAuthors(authors);

                JsonNode location = item.path("primary_location");
                paper.setVenue(location.path("source").path("display_name").asText(""));
                paper.setLandingUrl(location.path("landing_page_url").asText(""));
                paper.setPdfUrl(location.path("pdf_url").asText(""));
                JsonNode bestOa = item.path("best_oa_location");
                if (paper.getPdfUrl() == null || paper.getPdfUrl().isBlank()) paper.setPdfUrl(bestOa.path("pdf_url").asText(""));
                if (paper.getLandingUrl() == null || paper.getLandingUrl().isBlank()) paper.setLandingUrl(bestOa.path("landing_page_url").asText(""));
                if (paper.getLandingUrl() == null || paper.getLandingUrl().isBlank()) paper.setLandingUrl(item.path("open_access").path("oa_url").asText(""));
                paper.setSources(new ArrayList<>(List.of("OpenAlex")));
                papers.add(paper);
            }
            return papers;
        } catch (Exception e) {
            throw new IllegalStateException("OpenAlex 检索失败: " + e.getMessage(), e);
        }
    }

    private String normalizeQuery(String query) {
        if (query == null) return "";
        return query.replaceAll("(?i)\\bAND\\b|\\bOR\\b", " ")
                .replace('"', ' ').replaceAll("\\s+", " ").trim();
    }

    private String normalizeDoi(String doi) {
        if (doi == null) return "";
        return doi.trim().replaceFirst("(?i)^https?://(dx\\.)?doi\\.org/", "");
    }
}
