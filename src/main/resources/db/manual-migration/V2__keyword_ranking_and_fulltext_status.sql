-- 当前项目仍使用 Hibernate ddl-auto=update；此脚本用于生产环境或关闭 ddl-auto 后的正式迁移。
ALTER TABLE research_profile ADD COLUMN IF NOT EXISTS core_keywords_json CLOB;
ALTER TABLE research_profile ADD COLUMN IF NOT EXISTS method_keywords_json CLOB;
ALTER TABLE research_profile ADD COLUMN IF NOT EXISTS expanded_keywords_json CLOB;
ALTER TABLE research_profile ADD COLUMN IF NOT EXISTS excluded_keywords_json CLOB;
ALTER TABLE research_profile ADD COLUMN IF NOT EXISTS keyword_groups_json CLOB;

ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS quality_score DOUBLE DEFAULT 0;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS core_coverage_score DOUBLE DEFAULT 0;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS phrase_group_score DOUBLE DEFAULT 0;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS abstract_match_score DOUBLE DEFAULT 0;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS matched_core_keywords_json CLOB;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS matched_method_keywords_json CLOB;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS matched_keyword_groups_json CLOB;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS recommendation_reason VARCHAR(2000);
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS knowledge_status VARCHAR(40) DEFAULT 'NOT_INDEXED';
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS full_text_source VARCHAR(40);
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS full_text_downloaded_at TIMESTAMP;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS parsed_page_count INTEGER;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS section_count INTEGER;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS chunk_count INTEGER;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS table_detected BOOLEAN DEFAULT FALSE;
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS failure_stage VARCHAR(100);
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(2000);
ALTER TABLE project_paper ADD COLUMN IF NOT EXISTS knowledge_updated_at TIMESTAMP;

UPDATE project_paper
SET knowledge_status = CASE WHEN saved_to_knowledge = TRUE THEN 'ABSTRACT_ONLY' ELSE 'NOT_INDEXED' END,
    evidence_level = CASE WHEN saved_to_knowledge = TRUE THEN 'ABSTRACT' ELSE evidence_level END
WHERE knowledge_status IS NULL OR knowledge_status = 'NOT_INDEXED';
