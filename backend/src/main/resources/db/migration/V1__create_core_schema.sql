CREATE TABLE users (
  id VARCHAR(64) PRIMARY KEY,
  email VARCHAR(160) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  nickname VARCHAR(80) NOT NULL UNIQUE,
  avatar VARCHAR(500),
  role VARCHAR(32) NOT NULL,
  city VARCHAR(80),
  bio TEXT,
  interests TEXT,
  following_count INT NOT NULL DEFAULT 0,
  follower_count INT NOT NULL DEFAULT 0,
  credit INT NOT NULL DEFAULT 100,
  verified TINYINT(1) NOT NULL DEFAULT 0,
  activated TINYINT(1) NOT NULL DEFAULT 0,
  activation_token VARCHAR(128),
  status VARCHAR(32) NOT NULL DEFAULT '正常',
  ban_reason TEXT,
  ban_until DATE,
  merchant_name VARCHAR(160),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE sessions (
  token VARCHAR(128) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  expires_at DATETIME NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE merchant_applications (
  id VARCHAR(64) PRIMARY KEY,
  user_id VARCHAR(64) NOT NULL,
  merchant_name VARCHAR(160) NOT NULL,
  license_name VARCHAR(255),
  status VARCHAR(32) NOT NULL,
  reason TEXT,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  reviewed_at DATETIME,
  reviewer_id VARCHAR(64),
  CONSTRAINT fk_merchant_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE activities (
  id VARCHAR(64) PRIMARY KEY,
  title VARCHAR(160) NOT NULL,
  summary TEXT NOT NULL,
  description TEXT,
  category VARCHAR(80) NOT NULL,
  cover VARCHAR(500),
  date_label VARCHAR(80) NOT NULL,
  time_label VARCHAR(80) NOT NULL,
  start_at DATETIME,
  end_at DATETIME,
  registration_deadline DATETIME,
  location VARCHAR(255) NOT NULL,
  district VARCHAR(80),
  distance DECIMAL(8,2) NOT NULL DEFAULT 0,
  longitude DECIMAL(8,2) NOT NULL DEFAULT 50,
  latitude DECIMAL(8,2) NOT NULL DEFAULT 50,
  price DECIMAL(10,2) NOT NULL DEFAULT 0,
  capacity INT NOT NULL,
  joined_count INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL,
  organizer_id VARCHAR(64) NOT NULL,
  featured TINYINT(1) NOT NULL DEFAULT 0,
  safety_note TEXT,
  min_age INT NOT NULL DEFAULT 0,
  join_fields TEXT,
  offline_reason TEXT,
  published_at DATETIME,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_activities_status (status),
  INDEX idx_activities_category (category),
  CONSTRAINT fk_activities_organizer FOREIGN KEY (organizer_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE activity_tags (
  activity_id VARCHAR(64) NOT NULL,
  tag VARCHAR(80) NOT NULL,
  rank_order INT NOT NULL DEFAULT 0,
  PRIMARY KEY (activity_id, tag),
  CONSTRAINT fk_activity_tags_activity FOREIGN KEY (activity_id) REFERENCES activities(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE registrations (
  id VARCHAR(64) PRIMARY KEY,
  activity_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  queue_position INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  checked_in_at DATETIME,
  UNIQUE KEY uk_registration_user_activity (activity_id, user_id),
  INDEX idx_registration_status (activity_id, status),
  CONSTRAINT fk_registration_activity FOREIGN KEY (activity_id) REFERENCES activities(id),
  CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE review_tasks (
  id VARCHAR(64) PRIMARY KEY,
  type VARCHAR(40) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  title VARCHAR(180) NOT NULL,
  submitter VARCHAR(120) NOT NULL,
  risk VARCHAR(16) NOT NULL,
  reason TEXT,
  submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  status VARCHAR(32) NOT NULL DEFAULT '待审核',
  handled_at DATETIME,
  handler_id VARCHAR(64),
  handler_reason TEXT,
  INDEX idx_review_status (status),
  INDEX idx_review_type (type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE teams (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  description TEXT,
  cover VARCHAR(500),
  tags TEXT,
  members_count INT NOT NULL DEFAULT 0,
  capacity INT NOT NULL DEFAULT 100,
  join_mode VARCHAR(32) NOT NULL,
  active_now INT NOT NULL DEFAULT 0,
  status VARCHAR(32) NOT NULL DEFAULT '正常',
  stop_reason TEXT,
  owner_id VARCHAR(64) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  CONSTRAINT fk_teams_owner FOREIGN KEY (owner_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE team_members (
  team_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  role VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT '正常',
  joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (team_id, user_id),
  CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
  CONSTRAINT fk_team_members_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE conversations (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(120) NOT NULL,
  avatar VARCHAR(500),
  type VARCHAR(32) NOT NULL,
  team_id VARCHAR(64),
  friend_user_id VARCHAR(64),
  unread INT NOT NULL DEFAULT 0,
  last_message TEXT,
  last_time VARCHAR(40),
  online TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_conversations_team FOREIGN KEY (team_id) REFERENCES teams(id),
  CONSTRAINT fk_conversations_friend FOREIGN KEY (friend_user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE messages (
  id VARCHAR(64) PRIMARY KEY,
  conversation_id VARCHAR(64) NOT NULL,
  sender_id VARCHAR(64) NOT NULL,
  content TEXT NOT NULL,
  sent_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  mine TINYINT(1) NOT NULL DEFAULT 0,
  read_flag TINYINT(1) NOT NULL DEFAULT 0,
  recalled TINYINT(1) NOT NULL DEFAULT 0,
  CONSTRAINT fk_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE,
  CONSTRAINT fk_messages_sender FOREIGN KEY (sender_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE audit_logs (
  id VARCHAR(64) PRIMARY KEY,
  actor_id VARCHAR(64),
  action VARCHAR(80) NOT NULL,
  target_type VARCHAR(80) NOT NULL,
  target_id VARCHAR(64) NOT NULL,
  reason TEXT,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
