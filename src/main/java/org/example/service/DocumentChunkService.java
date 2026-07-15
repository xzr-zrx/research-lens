package org.example.service;

import org.example.config.DocumentChunkConfig;
import org.example.dto.DocumentChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档分片服务
 * 负责将长文档切分为多个小片段，避免单个片段超过 embedding 接口输入长度限制。
 */
@Service
public class DocumentChunkService {

    private static final Logger logger = LoggerFactory.getLogger(DocumentChunkService.class);

    @Autowired
    private DocumentChunkConfig chunkConfig;

    /**
     * 智能分片文档
     * 优先按 Markdown 标题、段落边界分割；如果 PDF 提取出的长段落没有空行，
     * 会进一步按固定长度强制切分，避免出现 10 万字符被当成 1 个分片的问题。
     */
    public List<DocumentChunk> chunkDocument(String content, String filePath) {
        List<DocumentChunk> chunks = new ArrayList<>();

        if (content == null || content.trim().isEmpty()) {
            logger.warn("文档内容为空: {}", filePath);
            return chunks;
        }

        int maxSize = Math.max(200, chunkConfig.getMaxSize());
        int overlap = Math.max(0, Math.min(chunkConfig.getOverlap(), maxSize / 2));

        List<Section> sections = splitByHeadings(content);
        int globalChunkIndex = 0;

        for (Section section : sections) {
            List<DocumentChunk> sectionChunks = chunkSection(section, globalChunkIndex, maxSize, overlap);
            chunks.addAll(sectionChunks);
            globalChunkIndex += sectionChunks.size();
        }

        logger.info("文档分片完成: {} -> {} 个分片, maxSize={}, overlap={}", filePath, chunks.size(), maxSize, overlap);
        return chunks;
    }

    /**
     * 对已识别的论文章节内容分片（不再做标题切分），为每个分片打上
     * section / page / tableNumber / chunkType 元数据。
     *
     * 用于论文全文结构化索引：调用方先用 PaperSectionParser 识别章节，
     * 再用本方法把每个章节切成适合 embedding 的小片。
     */
    public List<DocumentChunk> chunkSection(String content, String section, Integer page,
                                            String tableNumber, String chunkType) {
        List<DocumentChunk> chunks = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) return chunks;

        int maxSize = Math.max(200, chunkConfig.getMaxSize());
        int overlap = Math.max(0, Math.min(chunkConfig.getOverlap(), maxSize / 2));

        Section sec = new Section(section, content.trim(), 0);
        List<DocumentChunk> sectionChunks = chunkSection(sec, 0, maxSize, overlap);
        for (DocumentChunk chunk : sectionChunks) {
            chunk.setTitle(section);
            chunk.setPage(page);
            chunk.setTableNumber(tableNumber);
            chunk.setChunkType(chunkType);
            chunks.add(chunk);
        }
        return chunks;
    }

    /**
     * 按 Markdown 标题分割文档。
     * PDF / Word 通常没有 Markdown 标题，因此会把全文作为一个 Section，后续再继续切分。
     */
    private List<Section> splitByHeadings(String content) {
        List<Section> sections = new ArrayList<>();

        Pattern headingPattern = Pattern.compile("^(#{1,6})\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = headingPattern.matcher(content);

        int lastEnd = 0;
        String currentTitle = null;

        while (matcher.find()) {
            if (lastEnd < matcher.start()) {
                String sectionContent = content.substring(lastEnd, matcher.start()).trim();
                if (!sectionContent.isEmpty()) {
                    sections.add(new Section(currentTitle, sectionContent, lastEnd));
                }
            }
            currentTitle = matcher.group(2).trim();
            lastEnd = matcher.start();
        }

        if (lastEnd < content.length()) {
            String sectionContent = content.substring(lastEnd).trim();
            if (!sectionContent.isEmpty()) {
                sections.add(new Section(currentTitle, sectionContent, lastEnd));
            }
        }

        if (sections.isEmpty()) {
            sections.add(new Section(null, content.trim(), 0));
        }

        return sections;
    }

    /**
     * 对单个章节分片。
     */
    private List<DocumentChunk> chunkSection(Section section, int startChunkIndex, int maxSize, int overlap) {
        List<DocumentChunk> chunks = new ArrayList<>();
        String content = section.content == null ? "" : section.content.trim();
        String title = section.title;

        if (content.isEmpty()) {
            return chunks;
        }

        // 小章节直接返回。
        if (content.length() <= maxSize) {
            DocumentChunk chunk = new DocumentChunk(
                    content,
                    section.startIndex,
                    section.startIndex + content.length(),
                    startChunkIndex
            );
            chunk.setTitle(title);
            chunks.add(chunk);
            return chunks;
        }

        List<String> paragraphs = splitByParagraphs(content);
        StringBuilder currentChunk = new StringBuilder();
        int chunkIndex = startChunkIndex;
        int approxCurrentStart = section.startIndex;

        for (String paragraph : paragraphs) {
            if (paragraph == null || paragraph.trim().isEmpty()) {
                continue;
            }

            paragraph = paragraph.trim();

            // 关键修复：如果单个段落本身就超过 maxSize，先把当前缓存写出，
            // 再把这个超长段落按固定长度切开。
            if (paragraph.length() > maxSize) {
                if (currentChunk.length() > 0) {
                    ChunkBuildResult result = flushChunk(chunks, currentChunk, title, approxCurrentStart, chunkIndex, overlap);
                    chunkIndex = result.nextChunkIndex;
                    approxCurrentStart = result.nextStartIndex;
                    currentChunk = new StringBuilder(result.nextOverlap);
                }

                List<String> pieces = splitLongText(paragraph, maxSize, overlap);
                for (String piece : pieces) {
                    if (piece.trim().isEmpty()) {
                        continue;
                    }
                    DocumentChunk chunk = new DocumentChunk(
                            piece,
                            approxCurrentStart,
                            approxCurrentStart + piece.length(),
                            chunkIndex++
                    );
                    chunk.setTitle(title);
                    chunks.add(chunk);
                    approxCurrentStart += Math.max(1, piece.length() - overlap);
                }
                currentChunk = new StringBuilder();
                continue;
            }

            int addLength = paragraph.length() + 2;
            if (currentChunk.length() > 0 && currentChunk.length() + addLength > maxSize) {
                ChunkBuildResult result = flushChunk(chunks, currentChunk, title, approxCurrentStart, chunkIndex, overlap);
                chunkIndex = result.nextChunkIndex;
                approxCurrentStart = result.nextStartIndex;
                currentChunk = new StringBuilder(result.nextOverlap);
            }

            if (currentChunk.length() > 0) {
                currentChunk.append("\n\n");
            }
            currentChunk.append(paragraph);
        }

        if (currentChunk.length() > 0) {
            String chunkContent = currentChunk.toString().trim();
            if (!chunkContent.isEmpty()) {
                DocumentChunk chunk = new DocumentChunk(
                        chunkContent,
                        approxCurrentStart,
                        approxCurrentStart + chunkContent.length(),
                        chunkIndex
                );
                chunk.setTitle(title);
                chunks.add(chunk);
            }
        }

        return chunks;
    }

    private ChunkBuildResult flushChunk(List<DocumentChunk> chunks,
                                        StringBuilder currentChunk,
                                        String title,
                                        int currentStartIndex,
                                        int chunkIndex,
                                        int overlap) {
        String chunkContent = currentChunk.toString().trim();
        if (!chunkContent.isEmpty()) {
            DocumentChunk chunk = new DocumentChunk(
                    chunkContent,
                    currentStartIndex,
                    currentStartIndex + chunkContent.length(),
                    chunkIndex++
            );
            chunk.setTitle(title);
            chunks.add(chunk);
        }

        String overlapText = getOverlapText(chunkContent, overlap);
        int nextStartIndex = currentStartIndex + Math.max(1, chunkContent.length() - overlapText.length());
        return new ChunkBuildResult(chunkIndex, nextStartIndex, overlapText);
    }

    /**
     * 按段落分割。兼容 Windows / Linux 换行。
     */
    private List<String> splitByParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        String normalized = content.replace("\r\n", "\n").replace("\r", "\n");

        // 先按空行切；如果 PDF 提取结果几乎没有空行，后续 splitLongText 会兜底。
        String[] parts = normalized.split("\\n\\s*\\n+");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    /**
     * 强制切分超长文本，保证每段长度不超过 maxSize。
     */
    private List<String> splitLongText(String text, int maxSize, int overlap) {
        List<String> pieces = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return pieces;
        }

        String cleaned = text.trim();
        int start = 0;
        int length = cleaned.length();

        while (start < length) {
            int end = Math.min(start + maxSize, length);

            // 尽量在句号、分号、逗号、空格等边界截断，避免断句太生硬。
            if (end < length) {
                int boundary = findBestBoundary(cleaned, start, end);
                if (boundary > start + maxSize / 2) {
                    end = boundary;
                }
            }

            String piece = cleaned.substring(start, end).trim();
            if (!piece.isEmpty()) {
                pieces.add(piece);
            }

            if (end >= length) {
                break;
            }

            int nextStart = Math.max(end - overlap, start + 1);
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }

        return pieces;
    }

    private int findBestBoundary(String text, int start, int end) {
        String window = text.substring(start, end);
        char[] boundaries = {'。', '！', '？', ';', '；', '.', '!', '?', '\n', '，', ',', ' '};

        int best = -1;
        for (char boundary : boundaries) {
            int idx = window.lastIndexOf(boundary);
            if (idx > best) {
                best = idx;
            }
        }

        if (best >= 0) {
            return start + best + 1;
        }
        return end;
    }

    private String getOverlapText(String text, int overlapSize) {
        if (text == null || text.isEmpty() || overlapSize <= 0) {
            return "";
        }

        int actualOverlap = Math.min(overlapSize, text.length());
        String overlap = text.substring(text.length() - actualOverlap).trim();

        int lastSentenceEnd = Math.max(
                overlap.lastIndexOf('。'),
                Math.max(overlap.lastIndexOf('？'), overlap.lastIndexOf('！'))
        );

        if (lastSentenceEnd > actualOverlap / 2) {
            return overlap.substring(lastSentenceEnd + 1).trim();
        }
        return overlap;
    }

    private static class Section {
        String title;
        String content;
        int startIndex;

        Section(String title, String content, int startIndex) {
            this.title = title;
            this.content = content;
            this.startIndex = startIndex;
        }
    }

    private static class ChunkBuildResult {
        int nextChunkIndex;
        int nextStartIndex;
        String nextOverlap;

        ChunkBuildResult(int nextChunkIndex, int nextStartIndex, String nextOverlap) {
            this.nextChunkIndex = nextChunkIndex;
            this.nextStartIndex = nextStartIndex;
            this.nextOverlap = nextOverlap == null ? "" : nextOverlap;
        }
    }
}
