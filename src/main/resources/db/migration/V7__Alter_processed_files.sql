-- Drop Not Null From provider
ALTER TABLE processed_files
  ALTER COLUMN provider TYPE VARCHAR(50),
  ALTER COLUMN provider DROP NOT NULL;

-- Modify Processing Status Constraint to fix Status changes from PROCESSING to IN_PROGRESS
ALTER TABLE processed_files DROP CONSTRAINT chk_processing_status;
ALTER TABLE processed_files ADD CONSTRAINT chk_processing_status CHECK (
  processing_status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'FAILED', 'SKIPPED')
);
