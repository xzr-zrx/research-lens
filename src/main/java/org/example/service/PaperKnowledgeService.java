package org.example.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.example.config.FileUploadConfig;
import org.example.dto.DocumentChunk;
import org.example.entity.Paper;
import org.example.entity.ProjectPaper;
import org.example.enums.EvidenceLevel;
import org.example.enums.FullTextSource;
import org.example.enums.KnowledgeStatus;
import org.example.exception.NotFoundException;
import org.example.repository.PaperRepository;
import org.example.repository.ProjectPaperRepository;
import org.example.util.PaperSectionParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class PaperKnowledgeService {
    private static final Logger logger = LoggerFactory.getLogger(PaperKnowledgeService.class);

    private final ProjectPaperRepository projectPaperRepository;
    private final PaperRepository paperRepository;
    private final VectorIndexService vectorIndexService;
    private final DocumentParserService parserService;
    private final DocumentChunkService chunkService;
    private final FileUploadConfig fileUploadConfig;
    private final FullTextDownloadService downloadService;

    public PaperKnowledgeService(ProjectPaperRepository projectPaperRepository,
                                 PaperRepository paperRepository,
                                 VectorIndexService vectorIndexService,
                                 DocumentParserService parserService,
                                 DocumentChunkService chunkService,
                                 FileUploadConfig fileUploadConfig,
                                 FullTextDownloadService downloadService) {
        this.projectPaperRepository = projectPaperRepository;
        this.paperRepository = paperRepository;
        this.vectorIndexService = vectorIndexService;
        this.parserService = parserService;
        this.chunkService = chunkService;
        this.fileUploadConfig = fileUploadConfig;
        this.downloadService = downloadService;
    }

    /**
     * 全文优先入库。没有全文时尝试下载开放 PDF；失败后不会静默降级为摘要。
     */
    public ProjectPaper saveToKnowledge(Long projectPaperId) throws Exception {
        ProjectPaper pp = getProjectPaper(projectPaperId);
        Paper paper = getPaper(pp);
        boolean hadAbstractIndex = pp.isSavedToKnowledge()
                && pp.getEvidenceLevel() == EvidenceLevel.ABSTRACT
                && pp.getKnowledgeStatus() == KnowledgeStatus.ABSTRACT_ONLY;
        resetFailure(pp);
        try {
            if (!hasLocalFullText(paper)) {
                updateStatus(pp, KnowledgeStatus.FETCHING_FULL_TEXT, "FETCHING_FULL_TEXT", null);
                if (!downloadService.canDownload(paper)) {
                    updateStatus(pp, KnowledgeStatus.MANUAL_UPLOAD_REQUIRED, "FETCHING_FULL_TEXT", "没有开放全文地址，请手动上传 PDF");
                    throw new FullTextDownloadService.FullTextUnavailableException("没有开放全文地址，请手动上传全文，或明确选择仅摘要入库");
                }
                FullTextDownloadService.DownloadResult result = downloadService.download(paper);
                paper.setLocalFullTextPath(normalize(result.path()));
                paperRepository.save(paper);
                pp.setFullTextSource(result.source().name());
                pp.setFullTextDownloadedAt(LocalDateTime.now());
                projectPaperRepository.save(pp);
            } else if (pp.getFullTextSource() == null || pp.getFullTextSource().isBlank()) {
                pp.setFullTextSource(FullTextSource.UNKNOWN.name());
            }

            updateStatus(pp, KnowledgeStatus.PARSING_FULL_TEXT, "PARSING_FULL_TEXT", null);
            IndexStats stats = indexFullText(sourceId(pp), pp, paper);
            pp.setParsedPageCount(stats.pageCount());
            pp.setSectionCount(stats.sectionCount());
            pp.setChunkCount(stats.chunkCount());
            pp.setTableDetected(stats.tableDetected());
            pp.setEvidenceLevel(EvidenceLevel.FULL_TEXT);
            pp.setSavedToKnowledge(true);
            updateStatus(pp, KnowledgeStatus.FULL_TEXT_INDEXED, null, null);
            logger.info("论文全文入库完成: projectPaperId={}, paperId={}, chunks={}", pp.getId(), paper.getId(), stats.chunkCount());
            return projectPaperRepository.save(pp);
        } catch (FullTextDownloadService.FullTextUnavailableException e) {
            if (hadAbstractIndex) {
                pp.setSavedToKnowledge(true);
                pp.setEvidenceLevel(EvidenceLevel.ABSTRACT);
                pp.setKnowledgeStatus(KnowledgeStatus.ABSTRACT_ONLY);
                pp.setFailureStage("FETCHING_FULL_TEXT");
                pp.setFailureReason(concise(e));
                pp.setKnowledgeUpdatedAt(LocalDateTime.now());
                projectPaperRepository.save(pp);
            } else {
                pp.setSavedToKnowledge(false);
                updateStatus(pp, KnowledgeStatus.MANUAL_UPLOAD_REQUIRED, "FETCHING_FULL_TEXT", concise(e));
            }
            throw e;
        } catch (Exception e) {
            vectorIndexService.deleteFileVectors(sourceId(pp));
            pp.setSavedToKnowledge(false);
            pp.setEvidenceLevel(EvidenceLevel.ABSTRACT);
            updateStatus(pp, KnowledgeStatus.FAILED,
                    pp.getFailureStage() == null ? "UNKNOWN" : pp.getFailureStage(), concise(e));
            throw e;
        }
    }

    /** 用户明确选择后，才允许只将标题和摘要写入知识库。 */
    public ProjectPaper saveAbstractOnly(Long projectPaperId) throws Exception {
        ProjectPaper pp = getProjectPaper(projectPaperId);
        Paper paper = getPaper(pp);
        resetFailure(pp);
        updateStatus(pp, KnowledgeStatus.INDEXING, "INDEXING_ABSTRACT", null);
        try {
            indexAbstractOnly(sourceId(pp), pp, paper);
            pp.setSavedToKnowledge(true);
            pp.setEvidenceLevel(EvidenceLevel.ABSTRACT);
            updateStatus(pp, KnowledgeStatus.ABSTRACT_ONLY, null, null);
            return projectPaperRepository.save(pp);
        } catch (Exception e) {
            vectorIndexService.deleteFileVectors(sourceId(pp));
            pp.setSavedToKnowledge(false);
            updateStatus(pp, KnowledgeStatus.FAILED, "INDEXING_ABSTRACT", concise(e));
            throw e;
        }
    }

    public ProjectPaper retryFullText(Long projectPaperId) throws Exception { return saveToKnowledge(projectPaperId); }

    private void indexAbstractOnly(String sourceId, ProjectPaper pp, Paper paper) throws Exception {
        deleteLegacyPaperVectors(paper);
        String abstractText = paper.getAbstractText();
        if (abstractText == null || abstractText.isBlank()) throw new IllegalStateException("论文没有摘要，无法执行摘要入库");
        Map<String, Object> metadata = paperMetadata(pp, paper, EvidenceLevel.ABSTRACT);
        metadata.put("section", "Abstract"); metadata.put("chunkType", "ABSTRACT");
        String content = paper.getTitle() + "\n\n" + abstractText;
        List<DocumentChunk> chunks = chunkService.chunkSection(content, "Abstract", null, null, "ABSTRACT");
        if (chunks.isEmpty()) {
            DocumentChunk chunk = new DocumentChunk(content, 0, content.length(), 0);
            chunk.setTitle("Abstract"); chunk.setChunkType("ABSTRACT"); chunks = List.of(chunk);
        }
        vectorIndexService.indexChunks(sourceId, chunks, metadata);
        pp.setChunkCount(chunks.size()); pp.setSectionCount(1); pp.setParsedPageCount(null); pp.setTableDetected(false);
        logger.info("论文摘要已索引: paperId={}, 分片数={}", paper.getId(), chunks.size());
    }

    private IndexStats indexFullText(String sourceId, ProjectPaper pp, Paper paper) throws Exception {
        deleteLegacyPaperVectors(paper);
        Path fullTextPath = Path.of(paper.getLocalFullTextPath());
        if (!Files.isRegularFile(fullTextPath)) throw new IllegalStateException("本地全文文件不存在");
        List<DocumentChunk> chunks = new ArrayList<>();
        int pages = 0, sectionsCount = 0;
        boolean hasTable = false;
        String fileName = fullTextPath.getFileName() == null ? "" : fullTextPath.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            try (PDDocument document = PDDocument.load(fullTextPath.toFile())) { pages = document.getNumberOfPages(); }
            List<PaperSectionParser.PaperSection> sections = PaperSectionParser.parse(fullTextPath);
            if (sections.isEmpty()) throw new IllegalArgumentException("PDF 未解析出文本，可能是扫描版，请上传可复制文本的 PDF");
            sectionsCount = (int) sections.stream().map(PaperSectionParser.PaperSection::sectionName).distinct().count();
            hasTable = sections.stream().anyMatch(s -> "Table Caption".equals(s.sectionName()));
            for (PaperSectionParser.PaperSection section : sections) {
                chunks.addAll(chunkService.chunkSection(section.content(), section.sectionName(), section.page(),
                        section.tableNumber(), chunkTypeFor(section.sectionName())));
            }
        } else {
            String text = parserService.parse(fullTextPath);
            if (text == null || text.isBlank()) throw new IllegalArgumentException("全文未解析出文本");
            chunks = chunkService.chunkSection(text, "Full text", null, null, "FULL_TEXT");
            sectionsCount = 1;
        }
        if (chunks.isEmpty()) throw new IllegalArgumentException("全文解析后没有可索引内容");
        updateStatus(pp, KnowledgeStatus.INDEXING, "INDEXING", null);
        vectorIndexService.indexChunks(sourceId, chunks, paperMetadata(pp, paper, EvidenceLevel.FULL_TEXT));
        return new IndexStats(pages, sectionsCount, chunks.size(), hasTable);
    }

    public ProjectPaper uploadFullText(Long projectPaperId, MultipartFile file) throws Exception {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("上传文件不能为空");
        ProjectPaper pp = getProjectPaper(projectPaperId);
        Paper paper = getPaper(pp);
        String originalName = file.getOriginalFilename() == null ? "paper.pdf" : file.getOriginalFilename();
        Path dir = Paths.get(fileUploadConfig.getPath(), "papers", String.valueOf(paper.getId())).normalize();
        Files.createDirectories(dir);
        Path target = dir.resolve(originalName.replaceAll("[\\\\/:*?\"<>|]", "_")).normalize();
        if (!target.startsWith(dir)) throw new IllegalArgumentException("非法文件名");
        try (var input = file.getInputStream()) {
            Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
        }
        paper.setLocalFullTextPath(normalize(target));
        paperRepository.save(paper);
        pp.setFullTextSource(FullTextSource.LOCAL_UPLOAD.name());
        pp.setFullTextDownloadedAt(LocalDateTime.now());
        projectPaperRepository.save(pp);
        return saveToKnowledge(projectPaperId);
    }

    private Map<String, Object> paperMetadata(ProjectPaper pp, Paper paper, EvidenceLevel level) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("sourceType", "ACADEMIC_PAPER"); metadata.put("projectId", pp.getProjectId());
        metadata.put("paperId", paper.getId()); metadata.put("projectPaperId", pp.getId());
        metadata.put("title", paper.getTitle()); metadata.put("doi", paper.getDoi());
        metadata.put("arxivId", paper.getArxivId()); metadata.put("year", paper.getPublicationYear());
        metadata.put("evidenceLevel", level.name());
        return metadata;
    }

    private ProjectPaper getProjectPaper(Long id) {
        return projectPaperRepository.findById(id).orElseThrow(() -> new NotFoundException("项目论文不存在: " + id));
    }
    private Paper getPaper(ProjectPaper pp) {
        return paperRepository.findById(pp.getPaperId()).orElseThrow(() -> new NotFoundException("论文不存在: " + pp.getPaperId()));
    }
    private boolean hasLocalFullText(Paper paper) {
        return paper.getLocalFullTextPath() != null && !paper.getLocalFullTextPath().isBlank()
                && Files.isRegularFile(Path.of(paper.getLocalFullTextPath()));
    }
    private String chunkTypeFor(String sectionName) {
        if ("Table Caption".equals(sectionName)) return "TABLE";
        if ("Figure Caption".equals(sectionName)) return "FIGURE";
        return "FULL_TEXT";
    }
    private String sourceId(ProjectPaper pp) { return "project-paper://" + pp.getId(); }
    private void deleteLegacyPaperVectors(Paper paper) {
        // 旧版本使用 paper://{paperId}，同一论文跨项目会相互覆盖。新版本按 ProjectPaper 独立索引。
        vectorIndexService.deleteFileVectors("paper://" + paper.getId());
    }
    private void resetFailure(ProjectPaper pp) { pp.setFailureStage(null); pp.setFailureReason(null); }
    private void updateStatus(ProjectPaper pp, KnowledgeStatus status, String stage, String reason) {
        pp.setKnowledgeStatus(status); pp.setKnowledgeUpdatedAt(LocalDateTime.now());
        if (status == KnowledgeStatus.FULL_TEXT_INDEXED || status == KnowledgeStatus.ABSTRACT_ONLY) {
            pp.setFailureStage(null); pp.setFailureReason(null);
        } else {
            if (stage != null) pp.setFailureStage(stage);
            if (reason != null) pp.setFailureReason(reason);
        }
        projectPaperRepository.save(pp);
    }
    private String concise(Exception e) {
        String value = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return value.length() > 1900 ? value.substring(0, 1900) : value;
    }
    private String normalize(Path path) { return path.toString().replace('\\', '/'); }
    private record IndexStats(int pageCount, int sectionCount, int chunkCount, boolean tableDetected) {}
}
