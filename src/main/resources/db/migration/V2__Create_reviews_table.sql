CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    external_id VARCHAR(100) NOT NULL,
    provider_id BIGINT NOT NULL REFERENCES providers(id),
    property_id VARCHAR(100) NOT NULL,
    guest_name VARCHAR(200),
    rating DOUBLE PRECISION NOT NULL CHECK (rating >= 0.0 AND rating <= 5.0),
    review_text TEXT,
    review_date TIMESTAMP NOT NULL,
    language VARCHAR(5),
    title VARCHAR(500),
    stay_date TIMESTAMP,
    room_type VARCHAR(200),
    trip_type VARCHAR(50),
    helpful_votes INTEGER,
    verified BOOLEAN NOT NULL DEFAULT FALSE,
    processing_status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT,
    CONSTRAINT uk_review_provider_external_id UNIQUE (provider_id, external_id)
);

CREATE INDEX idx_review_provider_external_id ON reviews(provider_id, external_id);
CREATE INDEX idx_review_property_id ON reviews(property_id);
CREATE INDEX idx_review_rating ON reviews(rating);
CREATE INDEX idx_review_review_date ON reviews(review_date);
CREATE INDEX idx_review_language ON reviews(language);
CREATE INDEX idx_review_verified ON reviews(verified);
CREATE INDEX idx_review_processing_status ON reviews(processing_status);