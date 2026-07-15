package org.example.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import io.milvus.client.MilvusServiceClient;
import io.milvus.grpc.MutationResult;
import io.milvus.param.R;
import io.milvus.param.RpcStatus;
import io.milvus.param.collection.LoadCollectionParam;
import io.milvus.param.dml.DeleteParam;
import io.milvus.param.dml.InsertParam;
import org.example.constant.MilvusConstants;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class VectorIndexService {
    private static final Logger logger = LoggerFactory.getLogger(VectorIndexService.class);

    private final MilvusServiceClient milvusClient;
    private final VectorEmbeddingService embeddingService;
    private final DocumentChunkService chunkService;
    private final DocumentParserService documentParserService;
    private final Gson gson = new Gson();

    public VectorIndexService(MilvusServiceClient milvusClient,
                              VectorEmbeddingService embeddingService,
                              DocumentChunkService chunkService,
                              DocumentParserService documentParserService) {
        this.milvusClient = milvusClient;
        this.embeddingService = embeddingService;
        this.chunkService = chunkService;
        this.documentParserService = documentParserService;
    }

    public void indexSingleFile(String filePath) throws Exception {
        Path path = Paths.get(filePath).normalize();
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("文件不存在: " + filePath);
        String content = documentParserService.parse(path);
        if (content == null || content.isBlank()) throw new IllegalArgumentException("文件未解析出可索引文本");
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "USER_FILE");
        metadata.put("title", path.getFileName() == null ? filePath : path.getFileName().toString());
        metadata.put("evidenceLevel", "FULL_TEXT");
        indexTextDocument(normalize(path.toString()), content, metadata);
    }

    public void indexTextDocument(String sourceId, String content, Map<String, Object> baseMetadata) throws Exception {
        if (content == null || content.isBlank()) throw new IllegalArgumentException("索引内容不能为空");
        String normalizedSource = normalize(sourceId);
        deleteFileVectors(normalizedSource);
        List<DocumentChunk> chunks = chunkService.chunkDocument(content, normalizedSource);
        embedAndInsertChunks(normalizedSource, chunks, baseMetadata);
    }

    /**
     * 索引预先构建好的分片（不再二次切分）。用于论文结构化索引：
     * 调用方已用 PaperSectionParser + chunkService.chunkSection 切好带页码/章节的分片。
     */
    public void indexChunks(String sourceId, List<DocumentChunk> chunks, Map<String, Object> baseMetadata) throws Exception {
        if (chunks == null || chunks.isEmpty()) throw new IllegalArgumentException("索引分片不能为空");
        String normalizedSource = normalize(sourceId);
        deleteFileVectors(normalizedSource);
        embedAndInsertChunks(normalizedSource, chunks, baseMetadata);
    }

    private void embedAndInsertChunks(String normalizedSource, List<DocumentChunk> chunks,
                                      Map<String, Object> baseMetadata) throws Exception {
        for (DocumentChunk chunk : chunks) {
            if (chunk.getContent() == null || chunk.getContent().isBlank()) continue;
            List<Float> vector = embeddingService.generateEmbedding(chunk.getContent());
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (baseMetadata != null) metadata.putAll(baseMetadata);
            metadata.put("_source", normalizedSource);
            Object title = metadata.get("title");
            metadata.put("_file_name", title == null || title.toString().isBlank() ? sourceName(normalizedSource) : title);
            metadata.put("chunkIndex", chunk.getChunkIndex());
            metadata.put("totalChunks", chunks.size());
            if (chunk.getTitle() != null && !chunk.getTitle().isBlank()) metadata.put("section", chunk.getTitle());
            if (chunk.getPage() != null) metadata.put("page", chunk.getPage());
            if (chunk.getTableNumber() != null && !chunk.getTableNumber().isBlank()) metadata.put("tableNumber", chunk.getTableNumber());
            if (chunk.getChunkType() != null && !chunk.getChunkType().isBlank()) metadata.put("chunkType", chunk.getChunkType());
            insert(chunk.getContent(), vector, metadata, chunk.getChunkIndex());
        }
    }

    public IndexingResult indexDirectory(String directoryPath) {
        IndexingResult result = new IndexingResult();
        result.setDirectoryPath(directoryPath);
        result.setStartTime(LocalDateTime.now());
        try {
            Path dir = Paths.get(directoryPath).normalize();
            if (!Files.isDirectory(dir)) throw new IllegalArgumentException("目录不存在: " + directoryPath);
            List<Path> files;
            try (var stream = Files.walk(dir)) {
                files = stream.filter(Files::isRegularFile).filter(path -> isSupported(path.getFileName().toString())).toList();
            }
            result.setTotalFiles(files.size());
            for (Path file : files) {
                try {
                    indexSingleFile(file.toString());
                    result.incrementSuccessCount();
                } catch (Exception e) {
                    result.incrementFailCount();
                    result.addFailedFile(file.toString(), e.getMessage());
                }
            }
            result.setSuccess(result.getFailCount() == 0);
        } catch (Exception e) {
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        result.setEndTime(LocalDateTime.now());
        return result;
    }

    public void deleteFileVectors(String sourceId) {
        try {
            String source = normalize(sourceId).replace("\"", "\\\"");
            loadCollection();
            R<MutationResult> response = milvusClient.delete(DeleteParam.newBuilder()
                    .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                    .withExpr("metadata[\"_source\"] == \"" + source + "\"")
                    .build());
            if (response.getStatus() != 0) logger.warn("删除旧向量失败: {}", response.getMessage());
        } catch (Exception e) {
            logger.debug("删除旧向量跳过: {}", e.getMessage());
        }
    }

    private void insert(String content, List<Float> vector, Map<String, Object> metadata, int chunkIndex) {
        loadCollection();
        String source = String.valueOf(metadata.get("_source"));
        String id = UUID.nameUUIDFromBytes((source + "_" + chunkIndex).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
        JsonObject metadataJson = gson.toJsonTree(metadata).getAsJsonObject();
        List<InsertParam.Field> fields = List.of(
                new InsertParam.Field("id", Collections.singletonList(id)),
                new InsertParam.Field("content", Collections.singletonList(content)),
                new InsertParam.Field("vector", Collections.singletonList(vector)),
                new InsertParam.Field("metadata", Collections.singletonList(metadataJson))
        );
        R<MutationResult> response = milvusClient.insert(InsertParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME)
                .withFields(fields)
                .build());
        if (response.getStatus() != 0) throw new IllegalStateException("插入向量失败: " + response.getMessage());
    }

    private void loadCollection() {
        R<RpcStatus> response = milvusClient.loadCollection(LoadCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME).build());
        if (response.getStatus() != 0 && response.getStatus() != 65535) {
            throw new IllegalStateException("加载 Milvus Collection 失败: " + response.getMessage());
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.replace(File.separator, "/");
    }

    private String sourceName(String source) {
        if (source == null || source.isBlank()) return "unknown";
        String normalized = source.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 && slash < normalized.length() - 1 ? normalized.substring(slash + 1) : normalized;
    }

    private boolean isSupported(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown") || lower.endsWith(".note")
                || lower.endsWith(".pdf") || lower.endsWith(".doc") || lower.endsWith(".docx");
    }

    public static class IndexingResult {
        private boolean success;
        private String directoryPath;
        private int totalFiles;
        private int successCount;
        private int failCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private String errorMessage;
        private final Map<String, String> failedFiles = new LinkedHashMap<>();

        public void incrementSuccessCount() { successCount++; }
        public void incrementFailCount() { failCount++; }
        public void addFailedFile(String path, String error) { failedFiles.put(path, error); }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getDirectoryPath() { return directoryPath; }
        public void setDirectoryPath(String directoryPath) { this.directoryPath = directoryPath; }
        public int getTotalFiles() { return totalFiles; }
        public void setTotalFiles(int totalFiles) { this.totalFiles = totalFiles; }
        public int getSuccessCount() { return successCount; }
        public int getFailCount() { return failCount; }
        public LocalDateTime getStartTime() { return startTime; }
        public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
        public LocalDateTime getEndTime() { return endTime; }
        public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public Map<String, String> getFailedFiles() { return failedFiles; }
    }
}
