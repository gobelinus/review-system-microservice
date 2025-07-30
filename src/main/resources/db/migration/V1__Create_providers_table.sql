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
('Agoda', 'Agoda', 'HOTEL_BOOKING', 332, true),
('Booking', 'Booking', 'HOTEL_BOOKING', 333, true),
('Expedia', 'Expedia', 'HOTEL_BOOKING', 334, true);

-- Create index for provider lookups
CREATE INDEX idx_provider_code ON provider(code);
CREATE INDEX idx_provider_external_id ON provider(external_id);

-- Add comments
COMMENT ON TABLE provider IS 'Stores information about review providers like Agoda, Booking, etc.';
COMMENT ON COLUMN provider.external_id IS 'External provider ID from the JSON data';
COMMENT ON COLUMN provider.provider_type IS 'Type of provider (HOTEL_BOOKING, FLIGHT, etc.)';