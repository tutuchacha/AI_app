-- M3-02 要素抽取：analysis_type 增加 FIELDS
ALTER TABLE document_analysis
    MODIFY COLUMN analysis_type VARCHAR(16) NOT NULL
        COMMENT 'SUMMARY / OUTLINE / QUESTIONS / FIELDS';
