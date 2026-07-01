ALTER TABLE notifications ADD COLUMN sender_id VARCHAR(64) NULL AFTER user_id,
  ADD INDEX idx_notifications_sender (sender_id, created_at);
