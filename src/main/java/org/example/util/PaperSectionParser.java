package org.example.util;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 论文全文结构化解析器。
 *
 * 不再把 PDF 当成一整段文本按固定长度随意切分，而是：
 * 1. 按页提取文本，保留页码；
 * 2. 识别常见学术章节标题（Abstract / Method / Experimental Setup / Results /
 *    Discussion / Conclusion / Appendix 等）；
 * 3. 单独抽出 Table / Figure caption，并解析表格/图编号；
 * 4. 输出带 section / page / tableNumber 的章节，供索引时写入 Milvus 元数据。
 *
 * 说明：仅支持可复制文本型 PDF；扫描版需 OCR。
 */
public final class PaperSectionParser {
    private static final Logger logger = LoggerFactory.getLogger(PaperSectionParser.class);

    /** 章节标题：允许前导编号（1、2.、3.1 等），关键词大小写不敏感。 */
    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^\\s*(?:\\d+(?:\\.\\d+)*\\.?\\s+)?" +
                    "(Abstract|Introduction|Background|Related\\s+Work|Method(s)?|Materials?\\s+and\\s+Methods?|" +
                    "Experimental\\s+Setup|Experiments?|Setup|Results?|Evaluation|Discussion|Conclusion(s)?|" +
                    "Appendix|References|Acknowledg(e)?ments?)\\b\\s*:?",
            Pattern.CASE_INSENSITIVE);

    /** 表格 / 图标题：Table 1 / Figure 2 / Fig. 3 等。 */
    private static final Pattern CAPTION_HEADING = Pattern.compile(
            "^\\s*(Table|Figure|Fig)\\.?\\s*(\\d+)\\b",
            Pattern.CASE_INSENSITIVE);

    private PaperSectionParser() {}

    public static List<PaperSection> parse(Path pdfPath) throws IOException {
        List<PaperSection> sections = new ArrayList<>();
        try (PDDocument document = PDDocument.load(pdfPath.toFile())) {
            int pageCount = document.getNumberOfPages();
            String currentSection = "Unknown";
            StringBuilder buffer = new StringBuilder();
            int sectionStartPage = 1;

            for (int page = 1; page <= pageCount; page++) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String pageText = stripper.getText(document);
                if (pageText == null || pageText.isBlank()) continue;

                for (String line : pageText.split("\\r?\\n")) {
                    String trimmed = line.trim();
                    if (trimmed.isEmpty()) {
                        buffer.append('\n');
                        continue;
                    }

                    Matcher caption = CAPTION_HEADING.matcher(trimmed);
                    if (caption.find()) {
                        flush(sections, currentSection, sectionStartPage, page - 1 < sectionStartPage ? page : sectionStartPage, buffer);
                        buffer.setLength(0);
                        currentSection = caption.group(1).toLowerCase(Locale.ROOT).startsWith("fig") ? "Figure Caption" : "Table Caption";
                        sectionStartPage = page;
                        buffer.append(trimmed).append('\n');
                        continue;
                    }

                    Matcher heading = SECTION_HEADING.matcher(trimmed);
                    if (heading.find() && trimmed.length() <= 60) {
                        flush(sections, currentSection, sectionStartPage, page, buffer);
                        buffer.setLength(0);
                        currentSection = canonicalSection(heading.group(1));
                        sectionStartPage = page;
                        buffer.append(trimmed).append('\n');
                        continue;
                    }

                    buffer.append(trimmed).append('\n');
                }

                // 页边界：把当前累积内容按页落盘，保证每个分片页码准确。
                flush(sections, currentSection, sectionStartPage, page, buffer);
                buffer.setLength(0);
                sectionStartPage = page + 1;
            }

            if (buffer.length() > 0) {
                flush(sections, currentSection, sectionStartPage, pageCount, buffer);
            }
        }

        // 过滤空章节，并为每个章节尝试解析表格编号。
        List<PaperSection> cleaned = new ArrayList<>();
        for (PaperSection s : sections) {
            String content = s.content().trim();
            if (content.isEmpty()) continue;
            String tableNumber = null;
            if ("Table Caption".equals(s.sectionName())) {
                Matcher m = CAPTION_HEADING.matcher(content);
                if (m.find()) tableNumber = m.group(2);
            }
            cleaned.add(new PaperSection(s.sectionName(), s.page(), tableNumber, content));
        }
        logger.info("论文结构化解析完成: {} -> {} 个章节", pdfPath, cleaned.size());
        return cleaned;
    }

    private static void flush(List<PaperSection> sections, String section, int startPage, int currentPage,
                              StringBuilder buffer) {
        String content = buffer.toString().trim();
        if (content.isEmpty()) return;
        int page = startPage <= 0 ? Math.max(1, currentPage) : startPage;
        sections.add(new PaperSection(section, page, null, content));
    }

    private static String canonicalSection(String raw) {
        if (raw == null) return "Unknown";
        String lower = raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        if (lower.startsWith("abstract")) return "Abstract";
        if (lower.startsWith("introduction") || lower.startsWith("background")) return "Introduction";
        if (lower.startsWith("related")) return "Related Work";
        if (lower.startsWith("method") || lower.startsWith("materials")) return "Method";
        if (lower.startsWith("experimental") || lower.startsWith("setup") || lower.startsWith("experiment")) return "Experimental Setup";
        if (lower.startsWith("result") || lower.startsWith("evaluation")) return "Results";
        if (lower.startsWith("discussion")) return "Discussion";
        if (lower.startsWith("conclusion")) return "Conclusion";
        if (lower.startsWith("appendix")) return "Appendix";
        if (lower.startsWith("reference")) return "References";
        if (lower.startsWith("acknowledg")) return "Acknowledgements";
        return raw;
    }

    /** 解析出的论文章节。 */
    public record PaperSection(String sectionName, int page, String tableNumber, String content) {}
}
