ALTER TABLE users ADD COLUMN gender VARCHAR(20);
ALTER TABLE users ADD COLUMN birthday DATE;
ALTER TABLE users ADD COLUMN cover VARCHAR(500);
ALTER TABLE users ADD COLUMN merchant_nickname VARCHAR(120);
ALTER TABLE users ADD COLUMN merchant_fields TEXT;

ALTER TABLE merchant_applications ADD COLUMN license_url VARCHAR(500);

ALTER TABLE activities ADD COLUMN team_id VARCHAR(64);
ALTER TABLE activities ADD COLUMN visibility VARCHAR(32) NOT NULL DEFAULT 'PUBLIC';
ALTER TABLE activities ADD COLUMN ai_review_status VARCHAR(32);
ALTER TABLE activities ADD COLUMN ai_risk_labels TEXT;
ALTER TABLE activities ADD COLUMN review_decision VARCHAR(32);
ALTER TABLE activities ADD COLUMN review_reason TEXT;
ALTER TABLE activities ADD COLUMN submit_token VARCHAR(128);

ALTER TABLE registrations ADD COLUMN form_data TEXT;
ALTER TABLE registrations ADD COLUMN promoted_until DATETIME;
ALTER TABLE registrations ADD COLUMN promotion_sent_at DATETIME;

ALTER TABLE messages ADD COLUMN message_type VARCHAR(32) NOT NULL DEFAULT 'TEXT';
ALTER TABLE messages ADD COLUMN media_url VARCHAR(500);
ALTER TABLE messages ADD COLUMN location_lat DECIMAL(10,6);
ALTER TABLE messages ADD COLUMN location_lng DECIMAL(10,6);
ALTER TABLE messages ADD COLUMN recalled_at DATETIME;
ALTER TABLE messages ADD COLUMN forwarded_from_id VARCHAR(64);

CREATE TABLE IF NOT EXISTS files (
  id VARCHAR(64) PRIMARY KEY,
  owner_id VARCHAR(64),
  original_name VARCHAR(255) NOT NULL,
  content_type VARCHAR(120),
  size_bytes BIGINT NOT NULL DEFAULT 0,
  url VARCHAR(700) NOT NULL,
  storage_key VARCHAR(500),
  provider VARCHAR(40) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_files_owner (owner_id),
  CONSTRAINT fk_files_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS notifications (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  type VARCHAR(60) NOT NULL,
  title VARCHAR(180) NOT NULL,
  content TEXT,
  target_type VARCHAR(60),
  target_id VARCHAR(64),
  read_flag TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_notifications_user (user_id, read_flag, created_at),
  CONSTRAINT fk_notifications_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mail_outbox (
  id VARCHAR(64) PRIMARY KEY,
  recipient VARCHAR(180) NOT NULL,
  subject VARCHAR(220) NOT NULL,
  body TEXT NOT NULL,
  status VARCHAR(32) NOT NULL,
  error TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  sent_at DATETIME,
  INDEX idx_mail_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS third_party_events (
  id VARCHAR(64) PRIMARY KEY,
  provider VARCHAR(60) NOT NULL,
  operation VARCHAR(80) NOT NULL,
  status VARCHAR(32) NOT NULL,
  request_summary TEXT,
  response_summary TEXT,
  error TEXT,
  duration_ms INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_third_party_events (provider, operation, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS ai_audit_logs (
  id VARCHAR(64) PRIMARY KEY,
  activity_id VARCHAR(64),
  result VARCHAR(32) NOT NULL,
  risk_labels TEXT,
  reason TEXT,
  raw_response TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_ai_audit_activity (activity_id),
  CONSTRAINT fk_ai_audit_activity FOREIGN KEY (activity_id) REFERENCES activities(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_checkin_codes (
  id VARCHAR(64) PRIMARY KEY,
  activity_id VARCHAR(64) NOT NULL,
  organizer_id VARCHAR(64) NOT NULL,
  code VARCHAR(128) NOT NULL UNIQUE,
  location_required TINYINT(1) NOT NULL DEFAULT 0,
  expires_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_checkin_codes_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
  CONSTRAINT fk_checkin_codes_organizer FOREIGN KEY (organizer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_summaries (
  id VARCHAR(64) PRIMARY KEY,
  activity_id VARCHAR(64) NOT NULL,
  author_id VARCHAR(64) NOT NULL,
  title VARCHAR(180) NOT NULL,
  content TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '已发布',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_summaries_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
  CONSTRAINT fk_summaries_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS summary_images (
  id VARCHAR(64) PRIMARY KEY,
  summary_id VARCHAR(64) NOT NULL,
  file_id VARCHAR(64),
  url VARCHAR(700) NOT NULL,
  ai_category VARCHAR(80),
  confirmed_category VARCHAR(80),
  rank_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_summary_images_summary FOREIGN KEY (summary_id) REFERENCES activity_summaries(id) ON DELETE CASCADE,
  CONSTRAINT fk_summary_images_file FOREIGN KEY (file_id) REFERENCES files(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS activity_reviews_user (
  id VARCHAR(64) PRIMARY KEY,
  activity_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  rating INT NOT NULL,
  content TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_activity_review_user (activity_id, user_id),
  CONSTRAINT fk_activity_reviews_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
  CONSTRAINT fk_activity_reviews_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friend_requests (
  id VARCHAR(64) PRIMARY KEY,
  requester_id VARCHAR(64) NOT NULL,
  receiver_id VARCHAR(64) NOT NULL,
  source VARCHAR(40) NOT NULL DEFAULT 'PROFILE',
  message TEXT,
  status VARCHAR(32) NOT NULL DEFAULT '待处理',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at DATETIME,
  UNIQUE KEY uk_friend_request_open (requester_id, receiver_id, status),
  CONSTRAINT fk_friend_requests_requester FOREIGN KEY (requester_id) REFERENCES users(id),
  CONSTRAINT fk_friend_requests_receiver FOREIGN KEY (receiver_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS follows (
  follower_id VARCHAR(64) NOT NULL,
  followee_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (follower_id, followee_id),
  CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users(id),
  CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS friendships (
  user_id VARCHAR(64) NOT NULL,
  friend_id VARCHAR(64) NOT NULL,
  remark VARCHAR(100),
  group_name VARCHAR(100),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, friend_id),
  CONSTRAINT fk_friendships_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_friendships_friend FOREIGN KEY (friend_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_blocks (
  user_id VARCHAR(64) NOT NULL,
  blocked_user_id VARCHAR(64) NOT NULL,
  reason TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, blocked_user_id),
  CONSTRAINT fk_user_blocks_user FOREIGN KEY (user_id) REFERENCES users(id),
  CONSTRAINT fk_user_blocks_blocked FOREIGN KEY (blocked_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_join_requests (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '待审核',
  reason TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  handled_at DATETIME,
  handler_id VARCHAR(64),
  UNIQUE KEY uk_team_join_open (team_id, user_id, status),
  CONSTRAINT fk_team_join_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_join_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_announcements (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  author_id VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  mention_all TINYINT(1) NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_announcements_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_announcements_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_polls (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  author_id VARCHAR(64) NOT NULL,
  title VARCHAR(180) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '进行中',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_polls_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_polls_author FOREIGN KEY (author_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_poll_options (
  id VARCHAR(64) PRIMARY KEY,
  poll_id VARCHAR(64) NOT NULL,
  text VARCHAR(180) NOT NULL,
  rank_order INT NOT NULL DEFAULT 0,
  CONSTRAINT fk_team_poll_options_poll FOREIGN KEY (poll_id) REFERENCES team_polls(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_poll_votes (
  poll_id VARCHAR(64) NOT NULL,
  option_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (poll_id, user_id),
  CONSTRAINT fk_team_poll_votes_poll FOREIGN KEY (poll_id) REFERENCES team_polls(id) ON DELETE CASCADE,
  CONSTRAINT fk_team_poll_votes_option FOREIGN KEY (option_id) REFERENCES team_poll_options(id) ON DELETE CASCADE,
  CONSTRAINT fk_team_poll_votes_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_files (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  file_id VARCHAR(64) NOT NULL,
  uploader_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_files_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_files_file FOREIGN KEY (file_id) REFERENCES files(id),
  CONSTRAINT fk_team_files_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_albums (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  file_id VARCHAR(64),
  uploader_id VARCHAR(64) NOT NULL,
  url VARCHAR(700) NOT NULL,
  caption TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_albums_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_albums_file FOREIGN KEY (file_id) REFERENCES files(id),
  CONSTRAINT fk_team_albums_uploader FOREIGN KEY (uploader_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_points (
  team_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  points INT NOT NULL DEFAULT 0,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id, user_id),
  CONSTRAINT fk_team_points_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_points_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS team_reports (
  id VARCHAR(64) PRIMARY KEY,
  team_id VARCHAR(64) NOT NULL,
  reporter_id VARCHAR(64),
  reason TEXT NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '待处理',
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_team_reports_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_team_reports_reporter FOREIGN KEY (reporter_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS conversation_participants (
  conversation_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  last_read_at DATETIME,
  muted TINYINT(1) NOT NULL DEFAULT 0,
  pinned TINYINT(1) NOT NULL DEFAULT 0,
  PRIMARY KEY (conversation_id, user_id),
  CONSTRAINT fk_conv_participants_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_conv_participants_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO conversation_participants (conversation_id, user_id)
SELECT c.id, tm.user_id FROM conversations c JOIN team_members tm ON tm.team_id = c.team_id WHERE c.team_id IS NOT NULL;

INSERT IGNORE INTO conversation_participants (conversation_id, user_id)
SELECT id, 'u-001' FROM conversations WHERE friend_user_id IS NOT NULL;

CREATE INDEX idx_activities_public_search ON activities (visibility, status, category, published_at);
CREATE INDEX idx_activities_geo ON activities (longitude, latitude);
CREATE INDEX idx_registrations_user_status ON registrations (user_id, status);
