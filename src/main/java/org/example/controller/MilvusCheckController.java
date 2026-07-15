package org.example.controller;

import io.milvus.client.MilvusServiceClient;
import io.milvus.param.collection.HasCollectionParam;
import org.example.constant.MilvusConstants;
import org.example.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/system")
public class MilvusCheckController {
    private final MilvusServiceClient client;

    public MilvusCheckController(MilvusServiceClient client) {
        this.client = client;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        boolean exists = Boolean.TRUE.equals(client.hasCollection(HasCollectionParam.newBuilder()
                .withCollectionName(MilvusConstants.MILVUS_COLLECTION_NAME).build()).getData());
        return ApiResponse.ok(Map.of("backend", "UP", "milvusCollection", exists));
    }
}
