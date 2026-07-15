package org.example.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.SearchResults;
import io.milvus.param.MetricType;
import io.milvus.param.R;
import io.milvus.param.dml.QueryParam;
import io.milvus.param.dml.SearchParam;
import io.milvus.response.SearchResultsWrapper;
import org.example.constant.MilvusConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VectorSearchService {
    private static final Logger logger = LoggerFactory.getLogger(VectorSearchService.class);

    private final MilvusServiceClient milvusClient;
    private final VectorEmbeddingService embeddingService;
    private final ObjectMapper objectMapper;

    public VectorSearchService(MilvusServiceClient milvusClient,
                               VectorEmbeddingService embeddingService,
                               ObjectMapper objectMapper) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
        this.objectMapper = objectMapper;
    }

    public List<SearchResult> searchSimilarDocuments(String query, int topK) {
        return searchSimilarDocuments(query, topK, null);
    }

    public List<SearchResult> searchSimilarDocuments(String query, int topK, String filterExpression) {
        List<Float> vector = embeddingService.generateQueryVector(query);
        var builder = SearchParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withVectorFieldName("vector")
                .withVectors(Collections.singletonList(vector))
                .withTopK(topK)
                .withMetricType(MetricType.L2)
                .withOutFields(List.of("id", "content", "metadata"))
                .withParams("{\"nprobe\":10}");
        if (filterExpression != null && !filterExpression.isBlank()) builder.withExpr(filterExpression);
        SearchParam searchParam = builder.build();
        R<SearchResults> response = milvusClient.search(searchParam);
        if (response.getStatus() != 0) throw new IllegalStateException("向量检索失败: " + response.getMessage());
        SearchResultsWrapper wrapper = new SearchResultsWrapper(response.getData().getResults());
        List<SearchResult> results = new ArrayList<>();
        for (int i = 0; i < wrapper.getRowRecords(0).size(); i++) {
            SearchResult result = new SearchResult();
            result.setId(String.valueOf(wrapper.getIDScore(0).get(i).get("id")));
            result.setContent(String.valueOf(wrapper.getFieldData("content", 0).get(i)));
            float distance = wrapper.getIDScore(0).get(i).getScore();
            result.setDistance(distance);
            result.setScore(1.0f / (1.0f + Math.max(0.0f, distance)));
            Object metadata = wrapper.getFieldData("metadata", 0).get(i);
            result.setMetadata(parseMetadata(metadata));
            results.add(result);
        }
        return results;
    }

    private Map<String, Object> parseMetadata(Object value) {
        if (value == null) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(value.toString(), new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("raw", value.toString());
            return fallback;
        }
    }

    /**
     * 按 metadata 表达式统计满足条件的分片数量，用于诊断信息。
     * 例如 expr = 'metadata["chunkType"] == "FULL_TEXT"'。
     */
    public int countByExpr(String expr) {
        try {
            QueryParam queryParam = QueryParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr(expr)
                    .withOutFields(List.of("id"))
                    .withLimit(100000L)
                    .build();
            R<io.milvus.grpc.QueryResults> response = milvusClient.query(queryParam);
            if (response.getStatus() != 0) {
                logger.warn("诊断统计查询失败: {}", response.getMessage());
                return 0;
            }
            return (int) new io.milvus.response.QueryResultsWrapper(response.getData()).getRowCount();
        } catch (Exception e) {
            logger.debug("诊断统计跳过: {}", e.getMessage());
            return 0;
        }
    }

    public static class SearchResult {
        private String id;
        private String content;
        private float distance;
        private float score;
        private Map<String, Object> metadata = new LinkedHashMap<>();

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
        public float getDistance() { return distance; }
        public void setDistance(float distance) { this.distance = distance; }
        public float getScore() { return score; }
        public void setScore(float score) { this.score = score; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
    }
}
