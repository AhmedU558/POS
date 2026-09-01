ALTER TABLE sales
ADD COLUMN fbr_provider_code varchar(50),
ADD COLUMN fbr_environment varchar(20),
ADD COLUMN fbr_status varchar(30) DEFAULT 'NOT_CONFIGURED',
ADD COLUMN fbr_request_id varchar(100),
ADD COLUMN fbr_submitted_at timestamptz,
ADD COLUMN fbr_completed_at timestamptz,
ADD COLUMN fbr_error_message text;
