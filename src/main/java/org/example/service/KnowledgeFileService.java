package org.example.service;

import org.example.config.FileUploadConfig;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class KnowledgeFileService {
    private final FileUploadConfig config;
    private final VectorIndexService vectorIndexService;

    public KnowledgeFileService(FileUploadConfig config, VectorIndexService vectorIndexService) {
        this.config = config;
        this.vectorIndexService = vectorIndexService;
    }

    public List<Map<String, Object>> list() throws Exception {
        Path root = Paths.get(config.getPath()).normalize();
        if (!Files.exists(root)) return List.of();
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString))
                    .map(path -> Map.<String, Object>of(
                            "name", path.getFileName().toString(),
                            "path", path.toString().replace('\\', '/'),
                            "size", safeSize(path)
                    )).toList();
        }
    }

    public Map<String, Object> upload(MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("文件不能为空");
        String originalName = file.getOriginalFilename() == null ? "document.txt" : file.getOriginalFilename();
        String extension = originalName.contains(".") ? originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        if (!List.of(config.getAllowedExtensions().toLowerCase(Locale.ROOT).split(",")).contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }
        Path root = Paths.get(config.getPath(), "knowledge").normalize();
        Files.createDirectories(root);
        Path target = root.resolve(originalName.replaceAll("[\\\\/:*?\"<>|]", "_")).normalize();
        if (!target.startsWith(root)) throw new IllegalArgumentException("非法文件名");
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        vectorIndexService.indexSingleFile(target.toString());
        return Map.of("name", originalName, "path", target.toString().replace('\\', '/'));
    }

    public void delete(String path) throws Exception {
        Path target = Paths.get(path).normalize();
        Path root = Paths.get(config.getPath()).toAbsolutePath().normalize();
        Path absolute = target.toAbsolutePath().normalize();
        if (!absolute.startsWith(root)) throw new IllegalArgumentException("只能删除 uploads 目录中的文件");
        vectorIndexService.deleteFileVectors(target.toString());
        Files.deleteIfExists(target);
    }

    public VectorIndexService.IndexingResult reindex() {
        return vectorIndexService.indexDirectory(config.getPath());
    }

    private long safeSize(Path path) {
        try { return Files.size(path); } catch (Exception e) { return 0L; }
    }
}
