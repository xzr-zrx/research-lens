package org.example.entity;

import jakarta.persistence.*;
import org.example.enums.QueryType;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_query")
public class SearchQuery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long projectId;

    @Column(nullable = false, length = 30)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private QueryType queryType;

    @Column(nullable = false, length = 1000)
    private String queryText;

    private Integer resultCount = 0;

    /** 本次检索使用的开始年份（含） */
    private Integer paperStartYear;
    /** 本次检索使用的结束年份（含） */
    private Integer paperEndYear;

    private LocalDateTime executedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public QueryType getQueryType() { return queryType; }
    public void setQueryType(QueryType queryType) { this.queryType = queryType; }
    public String getQueryText() { return queryText; }
    public void setQueryText(String queryText) { this.queryText = queryText; }
    public Integer getResultCount() { return resultCount; }
    public void setResultCount(Integer resultCount) { this.resultCount = resultCount; }
    public Integer getPaperStartYear() { return paperStartYear; }
    public void setPaperStartYear(Integer paperStartYear) { this.paperStartYear = paperStartYear; }
    public Integer getPaperEndYear() { return paperEndYear; }
    public void setPaperEndYear(Integer paperEndYear) { this.paperEndYear = paperEndYear; }
    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
