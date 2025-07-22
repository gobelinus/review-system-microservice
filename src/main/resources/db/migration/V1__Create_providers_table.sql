CREATE TABLE providers (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    api_endpoint VARCHAR(255),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    rating_scale DOUBLE PRECISION,
    supported_languages VARCHAR(200),
    processing_priority INTEGER,
    max_file_size_mb INTEGER,
    batch_size INTEGER,
    s3_path VARCHAR(200),
    file_pattern VARCHAR(100),
    timezone VARCHAR(50),
    last_processed_at TIMESTAMP,
    last_processed_file VARCHAR(255),
    total_reviews_processed BIGINT,
    configuration TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT,
    CONSTRAINT uk_provider_code UNIQUE (code),
    CONSTRAINT uk_provider_name UNIQUE (name)
);

CREATE INDEX idx_provider_active ON providers(active);
CREATE INDEX idx_provider_priority ON providers(processing_priority);