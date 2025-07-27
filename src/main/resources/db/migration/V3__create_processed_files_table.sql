-- Migration script to create processed_files table
-- This table tracks files processed from S3 to ensure idempotent processing

CREATE TABLE processed_files (
    id BIGSERIAL PRIMARY KEY,
    s3_key VARCHAR(1024) NOT NULL,
    etag VARCHAR(255) NOT NULL,
    file_size BIGINT NOT NULL CHECK (file_size > 0),
    last_modified_date TIMESTAMP,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    records_processed INTEGER,
    records_failed INTEGER,
    records_skipped INTEGER,
    error_message VARCHAR(2000),
    provider VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP,
    processing_started_at TIMESTAMP,
    processing_completed_at TIMESTAMP,

    -- Constraints
    CONSTRAINT uk_processed_files_s3_key_etag UNIQUE (s3_key, etag),
    CONSTRAINT chk_processing_status CHECK (processing_status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED', 'SKIPPED')),
    CONSTRAINT chk_records_processed CHECK (records_processed IS NULL OR records_processed >= 0),
    CONSTRAINT chk_records_failed CHECK (records_failed IS NULL OR records_failed >= 0)
);

-- Indexes for performance optimization
CREATE INDEX idx_processed_files_s3_key ON processed_files(s3_key);
CREATE INDEX idx_processed_files_status ON processed_files(processing_status);
CREATE INDEX idx_processed_files_created_at ON processed_files(created_at);
CREATE INDEX idx_processed_files_last_modified ON processed_files(last_modified_date);
CREATE INDEX idx_processed_files_provider ON processed_files(provider);
CREATE INDEX idx_processed_files_provider_status ON processed_files(provider, processing_status);

-- Composite index for cleanup operations
CREATE INDEX idx_processed_files_cleanup ON processed_files(created_at, processing_status)
WHERE processing_status IN ('COMPLETED', 'FAILED', 'SKIPPED');

-- Index for finding stuck processing files
CREATE INDEX idx_processed_files_stuck ON processed_files(processing_started_at)
WHERE processing_status = 'PROCESSING';

-- Comments for documentation
COMMENT ON TABLE processed_files IS 'Tracks files processed from S3 to ensure idempotent processing';
COMMENT ON COLUMN processed_files.s3_key IS 'Full S3 key path of the processed file';
COMMENT ON COLUMN processed_files.etag IS 'S3 ETag for file version identification';
COMMENT ON COLUMN processed_files.file_size IS 'Size of the file in bytes';
COMMENT ON COLUMN processed_files.last_modified_date IS 'Last modified date from S3 object metadata';
COMMENT ON COLUMN processed_files.processing_status IS 'Current processing status of the file';
COMMENT ON COLUMN processed_files.records_processed IS 'Number of records successfully processed from the file';
COMMENT ON COLUMN processed_files.records_failed IS 'Number of records that failed processing from the file';
COMMENT ON COLUMN processed_files.error_message IS 'Error message if processing failed';
COMMENT ON COLUMN processed_files.provider IS 'Source provider (agoda, booking, expedia)';
COMMENT ON COLUMN processed_files.created_at IS 'Timestamp when the record was created';
COMMENT ON COLUMN processed_files.updated_at IS 'Timestamp when the record was last updated';
COMMENT ON COLUMN processed_files.processing_started_at IS 'Timestamp when processing started';
COMMENT ON COLUMN processed_files.processing_completed_at IS 'Timestamp when processing completed or failed';