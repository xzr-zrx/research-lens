package org.example.dto;


/**
 * 文档分片
 */
public class DocumentChunk {

    // Getters and Setters
    /**
     * 分片内容
     */
    private String content;
    
    /**
     * 分片在原文档中的起始位置
     */
    private int startIndex;
    
    /**
     * 分片在原文档中的结束位置
     */
    private int endIndex;
    
    /**
     * 分片序号（从0开始）
     */
    private int chunkIndex;
    
    /**
     * 分片标题或上下文信息（论文章节名）
     */
    private String title;

    /**
     * 论文页码（结构化解析得到，普通文件为 null）
     */
    private Integer page;

    /**
     * 表格编号（如 "3"），仅 Table Caption 分片有值
     */
    private String tableNumber;

    /**
     * 证据类型：ABSTRACT / FULL_TEXT / TABLE / FIGURE / USER_FILE
     */
    private String chunkType;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                '}';
    }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public int getStartIndex() { return startIndex; }
    public void setStartIndex(int startIndex) { this.startIndex = startIndex; }
    public int getEndIndex() { return endIndex; }
    public void setEndIndex(int endIndex) { this.endIndex = endIndex; }
    public int getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(int chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public String getTableNumber() { return tableNumber; }
    public void setTableNumber(String tableNumber) { this.tableNumber = tableNumber; }
    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }
}
