package org.example.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 文档解析服务。
 *
 * 将用户上传的 PDF / Word / Markdown / TXT / 笔记文件统一解析为纯文本，
 * 供后续分片、Embedding 和 Milvus 入库使用。
 *
 * 说明：PDF 仅支持可复制文本型 PDF；扫描版 PDF 需要另接 OCR 模块。
 */
@Service
public class DocumentParserService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParserService.class);

    public String parse(Path path) throws IOException {
        String fileName = path.getFileName() == null ? "" : path.getFileName().toString();
        String extension = getExtension(fileName).toLowerCase(Locale.ROOT);

        logger.info("开始解析文档: {}, 类型: {}", path, extension);

        return switch (extension) {
            case "txt", "md", "markdown", "note" -> parsePlainText(path);
            case "pdf" -> parsePdf(path);
            case "docx" -> parseDocx(path);
            case "doc" -> parseDoc(path);
            default -> throw new IllegalArgumentException("暂不支持的文件类型: " + extension);
        };
    }

    private String parsePlainText(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    private String parsePdf(Path path) throws IOException {
        try (PDDocument document = PDDocument.load(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            return stripper.getText(document);
        }
    }

    private String parseDocx(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            return document.getParagraphs()
                    .stream()
                    .map(XWPFParagraph::getText)
                    .filter(text -> text != null && !text.isBlank())
                    .collect(Collectors.joining("\n\n"));
        }
    }

    private String parseDoc(Path path) throws IOException {
        try (InputStream inputStream = Files.newInputStream(path);
             HWPFDocument document = new HWPFDocument(inputStream);
             WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String getExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(index + 1);
    }
}
