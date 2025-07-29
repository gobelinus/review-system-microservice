-- V3__Create_review_table.sql
-- Create main review table based on the sample JSON structure

CREATE TABLE review (
    id BIGSERIAL PRIMARY KEY,

    -- Hotel and Provider Information
    hotel_id BIGINT NOT NULL,
    hotel_name VARCHAR(500) NOT NULL,
    provider_id BIGINT NOT NULL REFERENCES provider(id),
    platform VARCHAR(100) NOT NULL,

    -- Review Basic Information
    hotel_review_id BIGINT NOT NULL,
    provider_external_id INTEGER,
    rating DECIMAL(3,1),
    formatted_rating VARCHAR(10),
    rating_text VARCHAR(50),

    -- Review Content
    review_title VARCHAR(1000),
    review_comments TEXT,
    review_positives TEXT,
    review_negatives TEXT,
    original_title TEXT,
    original_comment TEXT,

    -- Translation Information
    translate_source VARCHAR(10),
    translate_target VARCHAR(10),

    -- Date Information
    review_date TIMESTAMP WITH TIME ZONE,
    formatted_review_date VARCHAR(100),
    check_in_date_month_and_year VARCHAR(50),

    -- Reviewer Information
    reviewer_display_name VARCHAR(200),
    reviewer_country_name VARCHAR(100),
    reviewer_country_id INTEGER,
    reviewer_flag_name VARCHAR(10),
    review_group_name VARCHAR(100),
    review_group_id INTEGER,
    room_type_name VARCHAR(200),
    room_type_id INTEGER,
    length_of_stay INTEGER,
    reviewer_review_count INTEGER DEFAULT 0,
    is_expert_reviewer BOOLEAN DEFAULT FALSE,
    is_show_global_icon BOOLEAN DEFAULT FALSE,
    is_show_reviewed_count BOOLEAN DEFAULT FALSE,

    -- Response Information
    is_show_review_response BOOLEAN DEFAULT FALSE,
    responder_name VARCHAR(200),
    response_date_text VARCHAR(100),
    formatted_response_date VARCHAR(100),
    response_translate_source VARCHAR(10),

    -- Provider Specific
    review_provider_logo VARCHAR(500),
    review_provider_text VARCHAR(100),
    encrypted_review_data VARCHAR(500),

    -- Processing Information
    processed_file_id BIGINT REFERENCES processed_files(id),
    raw_json_data JSONB, -- Store original JSON for debugging
    processing_status VARCHAR(50) DEFAULT 'ACTIVE',

    -- Audit fields
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for efficient querying
CREATE INDEX idx_review_hotel_id ON review(hotel_id);
CREATE INDEX idx_review_provider_id ON review(provider_id);
CREATE INDEX idx_review_hotel_review_id ON review(hotel_review_id);
CREATE INDEX idx_review_date ON review(review_date);
CREATE INDEX idx_review_rating ON review(rating);
CREATE INDEX idx_review_hotel_name ON review(hotel_name);
CREATE INDEX idx_review_reviewer_country ON review(reviewer_country_name);
CREATE INDEX idx_review_platform ON review(platform);
CREATE INDEX idx_review_processed_file ON review(processed_file_id);
CREATE INDEX idx_review_status ON review(processing_status);

-- Composite indexes for common queries
CREATE INDEX idx_review_hotel_provider ON review(hotel_id, provider_id);
CREATE INDEX idx_review_date_rating ON review(review_date, rating);
CREATE INDEX idx_review_hotel_date ON review(hotel_id, review_date DESC);

-- Unique constraint to prevent duplicate reviews
CREATE UNIQUE INDEX idx_review_unique ON review(hotel_review_id, provider_external_id, provider_id);

-- Add comments
COMMENT ON TABLE review IS 'Stores hotel reviews from various providers (Agoda, Booking.com, Expedia)';
COMMENT ON COLUMN review.hotel_review_id IS 'Review ID from the external provider';
COMMENT ON COLUMN review.provider_external_id IS 'Provider ID from the JSON (e.g., 332 for Agoda)';
COMMENT ON COLUMN review.raw_json_data IS 'Original JSON data for debugging and future processing';
COMMENT ON COLUMN review.encrypted_review_data IS 'Encrypted review data from provider';
COMMENT ON COLUMN review.processing_status IS 'Status: ACTIVE, DELETED, FLAGGED, PENDING_REVIEW';