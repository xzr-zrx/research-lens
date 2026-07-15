package org.example.service;

import org.example.config.FileUploadConfig;
import org.example.entity.ResearchInput;
import org.example.enums.InputType;
import org.example.exception.NotFoundException;
import org.example.repository.ResearchInputRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ResearchInputService {
    private final ResearchInputRepository repository;
    private final ResearchProjectService projectService;
    private final DocumentParserService parserService;
    private final FileUploadConfig fileUploadConfig;

    public ResearchInputService(ResearchInputRepository repository,
                                ResearchProjectService projectService,
                                DocumentParserService parserService,
                                FileUploadConfig fileUploadConfig) {
        this.repository = repository;
        this.projectService = projectService;
        this.parserService = parserService;
        this.fileUploadConfig = fileUploadConfig;
    }

    public List<ResearchInput> list(Long projectId) {
        projectService.get(projectId);
        return repository.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    public ResearchInput addText(Long projectId, String text) {
        projectService.get(projectId);
        if (text == null || text.isBlank()) throw new IllegalArgumentException("研究想法不能为空");
        ResearchInput input = new ResearchInput();
        input.setProjectId(projectId);
        input.setInputType(InputType.TEXT);
        input.setOriginalText(text.trim());
        input.setParsedText(text.trim());
        return repository.save(input);
    }

    public ResearchInput addFile(Long projectId, MultipartFile file) throws IOException {
        projectService.get(projectId);
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        String originalName = file.getOriginalFilename() == null ? "research-file" : file.getOriginalFilename();
        String extension = getExtension(originalName);
        Set<String> allowed = Set.of(fileUploadConfig.getAllowedExtensions().toLowerCase(Locale.ROOT).split(","));
        if (!allowed.contains(extension.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("暂不支持该文件类型: " + extension);
        }

        Path dir = Paths.get(fileUploadConfig.getPath(), "research", String.valueOf(projectId)).normalize();
        Files.createDirectories(dir);
        String safeName = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        Path target = dir.resolve(safeName).normalize();
        if (!target.startsWith(dir)) throw new IllegalArgumentException("非法文件名");
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String parsed = parserService.parse(target);
        if (parsed == null || parsed.isBlank()) {
            throw new IllegalArgumentException("文件未解析出文本；扫描版 PDF 目前不支持 OCR");
        }

        ResearchInput input = new ResearchInput();
        input.setProjectId(projectId);
        input.setInputType(InputType.FILE);
        input.setFileName(originalName);
        input.setFilePath(target.toString().replace('\\', '/'));
        input.setParsedText(parsed);
        return repository.save(input);
    }

    public String mergedText(Long projectId) {
        List<ResearchInput> inputs = list(projectId);
        if (inputs.isEmpty()) throw new NotFoundException("请先输入研究想法或上传实验记录");
        StringBuilder builder = new StringBuilder();
        for (ResearchInput input : inputs) {
            String text = input.getParsedText();
            if (text != null && !text.isBlank()) {
                builder.append("\n--- 输入 ").append(input.getId()).append(" ---\n").append(text.trim()).append('\n');
            }
        }
        return builder.toString().trim();
    }

    private String getExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && dot < fileName.length() - 1 ? fileName.substring(dot + 1) : "";
    }
}
