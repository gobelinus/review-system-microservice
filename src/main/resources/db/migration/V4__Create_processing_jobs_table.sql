-- Create processing_jobs table
CREATE TABLE processing_jobs (
    processing_id VARCHAR(50) PRIMARY KEY,
    status VARCHAR(20) NOT NULL,
    provider VARCHAR(20),
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    total_files INTEGER,
    processed_files INTEGER,
    failed_files INTEGER,
    total_reviews INTEGER,
    error_message VARCHAR(1000),
    triggered_by VARCHAR(100),
    is_asynchronous BOOLEAN,
    s3_prefix VARCHAR(500),
    max_files INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create processing_job_files table for processed file names
CREATE TABLE processing_job_files (
    processing_id VARCHAR(50) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    FOREIGN KEY (processing_id) REFERENCES processing_jobs(processing_id) ON DELETE CASCADE
);

-- Create processing_job_failed_files table for failed file names
CREATE TABLE processing_job_failed_files (
    processing_id VARCHAR(50) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    FOREIGN KEY (processing_id) REFERENCES processing_jobs(processing_id) ON DELETE CASCADE
);

-- Add indexes for better query performance
CREATE INDEX idx_processing_jobs_status ON processing_jobs(status);
CREATE INDEX idx_processing_jobs_provider ON processing_jobs(provider);
CREATE INDEX idx_processing_jobs_created_at ON processing_jobs(created_at);
CREATE INDEX idx_processing_jobs_start_time ON processing_jobs(start_time);
CREATE INDEX idx_processing_jobs_end_time ON processing_jobs(end_time);
CREATE INDEX idx_processing_jobs_provider_created_at ON processing_jobs(provider, created_at);
CREATE INDEX idx_processing_jobs_status_created_at ON processing_jobs(status, created_at);

-- Add indexes for file tracking tables
CREATE INDEX idx_processing_job_files_processing_id ON processing_job_files(processing_id);
CREATE INDEX idx_processing_job_failed_files_processing_id ON processing_job_failed_files(processing_id);