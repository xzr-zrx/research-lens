package org.example.entity;

import jakarta.persistence.*;
import org.example.enums.ProjectStatus;

import java.time.LocalDateTime;

@Entity
@Table(name = "research_project")
public class ResearchProject {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** 论文时间范围预设：RECENT_3_YEARS / RECENT_5_YEARS / SINCE_2020 / CUSTOM / ALL_TIME */
    @Column(length = 30)
    private String paperTimePreset;

    /** 论文检索开始年份（含） */
    private Integer paperStartYear;

    /** 论文检索结束年份（含） */
    private Integer paperEndYear;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = ProjectStatus.DRAFT;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public String getPaperTimePreset() { return paperTimePreset; }
    public void setPaperTimePreset(String paperTimePreset) { this.paperTimePreset = paperTimePreset; }
    public Integer getPaperStartYear() { return paperStartYear; }
    public void setPaperStartYear(Integer paperStartYear) { this.paperStartYear = paperStartYear; }
    public Integer getPaperEndYear() { return paperEndYear; }
    public void setPaperEndYear(Integer paperEndYear) { this.paperEndYear = paperEndYear; }
}
