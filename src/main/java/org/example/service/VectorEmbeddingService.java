package org.example.service;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.utils.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class VectorEmbeddingService {
    @Value("${spring.ai.dashscope.api-key:YOUR_DASHSCOPE_API_KEY}")
    private String apiKey;

    @Value("${dashscope.embedding.model:text-embedding-v4}")
    private String model;

    private final TextEmbedding textEmbedding = new TextEmbedding();

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.startsWith("YOUR_");
    }

    public List<Float> generateEmbedding(String content) {
        List<List<Float>> result = generateEmbeddings(Collections.singletonList(content));
        if (result.isEmpty()) throw new IllegalStateException("Embedding 服务没有返回结果");
        return result.get(0);
    }

    public List<List<Float>> generateEmbeddings(List<String> contents) {
        if (!isConfigured()) {
            throw new IllegalStateException("请先在 application.yml 中配置 DashScope API Key");
        }
        if (contents == null || contents.isEmpty()) return Collections.emptyList();
        List<String> safeContents = contents.stream()
                .map(text -> text == null ? "" : text.trim())
                .filter(text -> !text.isBlank())
                .toList();
        if (safeContents.isEmpty()) return Collections.emptyList();

        try {
            Constants.apiKey = apiKey;
            TextEmbeddingParam param = TextEmbeddingParam.builder()
                    .model(model)
                    .texts(safeContents)
                    .build();
            TextEmbeddingResult result = textEmbedding.call(param);
            if (result == null || result.getOutput() == null || result.getOutput().getEmbeddings() == null) {
                throw new IllegalStateException("DashScope Embedding 返回空结果");
            }
            List<List<Float>> embeddings = new ArrayList<>();
            for (TextEmbeddingResultItem item : result.getOutput().getEmbeddings()) {
                List<Float> vector = new ArrayList<>();
                for (Double value : item.getEmbedding()) vector.add(value.floatValue());
                embeddings.add(vector);
            }
            return embeddings;
        } catch (Exception e) {
            throw new IllegalStateException("生成文本向量失败: " + e.getMessage(), e);
        }
    }

    public List<Float> generateQueryVector(String query) {
        return generateEmbedding(query);
    }

    public float calculateCosineSimilarity(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.size() != b.size() || a.isEmpty()) return 0.0f;
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            na += x * x;
            nb += y * y;
        }
        if (na == 0 || nb == 0) return 0.0f;
        return (float) (dot / (Math.sqrt(na) * Math.sqrt(nb)));
    }
}
