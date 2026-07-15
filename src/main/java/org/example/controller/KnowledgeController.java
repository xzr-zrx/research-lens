package org.example.controller;

import org.example.dto.ApiResponse;
import org.example.service.KnowledgeFileService;
import org.example.service.VectorIndexService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {
    private final KnowledgeFileService service;

    public KnowledgeController(KnowledgeFileService service) {
        this.service = service;
    }

    @GetMapping("/files")
    public ApiResponse<List<Map<String, Object>>> list() throws Exception {
        return ApiResponse.ok(service.list());
    }

    @PostMapping(value = "/files", consumes = "multipart/form-data")
    public ApiResponse<Map<String, Object>> upload(@RequestPart("file") MultipartFile file) throws Exception {
        return ApiResponse.ok(service.upload(file));
    }

    @DeleteMapping("/files")
    public ApiResponse<Void> delete(@RequestParam String path) throws Exception {
        service.delete(path);
        return ApiResponse.ok("文件已删除", null);
    }

    @PostMapping("/reindex")
    public ApiResponse<VectorIndexService.IndexingResult> reindex() {
        return ApiResponse.ok(service.reindex());
    }
}
