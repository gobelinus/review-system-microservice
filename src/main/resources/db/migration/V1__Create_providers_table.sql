-- V1__Create_provider_table.sql
-- Create provider table to store review providers (Agoda, Booking.com, Expedia)

CREATE TABLE provider (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    code VARCHAR(100) NOT NULL UNIQUE,
    provider_type VARCHAR(50) NOT NULL,
    external_id INTEGER UNIQUE,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Insert default providers
INSERT INTO provider (name, code, provider_type, external_id, is_active) VALUES
('Agoda.com', 'Agoda', 'HOTEL_BOOKING', 332, true),
('Booking.com', 'Booking', 'HOTEL_BOOKING', 333, true),
('Expedia.com', 'Expedia', 'HOTEL_BOOKING', 334, true);

-- Create index for provider lookups
CREATE INDEX idx_provider_provider_id ON provider(provider_id);
CREATE INDEX idx_provider_type ON provider(provider_type);

-- Add comments
COMMENT ON TABLE provider IS 'Stores information about review providers like Agoda, Booking.com, etc.';
COMMENT ON COLUMN provider.provider_id IS 'External provider ID from the JSON data';
COMMENT ON COLUMN provider.provider_type IS 'Type of provider (HOTEL_BOOKING, FLIGHT, etc.)';