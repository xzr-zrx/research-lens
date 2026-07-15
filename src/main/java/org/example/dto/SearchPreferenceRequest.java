package org.example.dto;

/**
 * 更新论文检索时间范围的请求体。
 */
public class SearchPreferenceRequest {
    private String paperTimePreset;
    private Integer paperStartYear;
    private Integer paperEndYear;

    public String getPaperTimePreset() { return paperTimePreset; }
    public void setPaperTimePreset(String paperTimePreset) { this.paperTimePreset = paperTimePreset; }
    public Integer getPaperStartYear() { return paperStartYear; }
    public void setPaperStartYear(Integer paperStartYear) { this.paperStartYear = paperStartYear; }
    public Integer getPaperEndYear() { return paperEndYear; }
    public void setPaperEndYear(Integer paperEndYear) { this.paperEndYear = paperEndYear; }
}
