-- Interview Practice Service - Database Schema

CREATE DATABASE IF NOT EXISTS interview_practice;
USE interview_practice;

-- Completed sessions for historical analysis
CREATE TABLE IF NOT EXISTS interview_sessions (
  id VARCHAR(36) PRIMARY KEY,
  user_id BIGINT NOT NULL,
  resume_id BIGINT NOT NULL,
  job_listing_id BIGINT,
  interview_type ENUM('TECHNICAL', 'BEHAVIORAL', 'LEADERSHIP') NOT NULL,
  difficulty_level ENUM('JUNIOR', 'MID', 'SENIOR', 'STAFF') NOT NULL,
  total_rounds INT NOT NULL,
  avg_score DECIMAL(3,2),
  started_at TIMESTAMP NOT NULL,
  completed_at TIMESTAMP NOT NULL,
  duration_seconds INT,
  INDEX idx_user_date (user_id, completed_at),
  INDEX idx_resume (resume_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Q&A transcript for LLM training
CREATE TABLE IF NOT EXISTS interview_transcript (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  session_id VARCHAR(36) NOT NULL,
  round_number INT NOT NULL,
  question_text TEXT NOT NULL,
  answer_text TEXT NOT NULL,
  score DECIMAL(3,2),
  analysis JSON,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (session_id) REFERENCES interview_sessions(id),
  INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- User progress tracking for incremental improvement
CREATE TABLE IF NOT EXISTS user_progress (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  skill_area VARCHAR(100),
  session_count INT DEFAULT 0,
  avg_score DECIMAL(3,2),
  trend ENUM('IMPROVING', 'STABLE', 'DECLINING'),
  last_session_id VARCHAR(36),
  last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY unique_user_skill (user_id, skill_area),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Session scheduling
CREATE TABLE IF NOT EXISTS scheduled_sessions (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  schedule_type ENUM('DAILY', 'WEEKLY') DEFAULT 'DAILY',
  schedule_time TIME NOT NULL,
  interview_type ENUM('TECHNICAL', 'BEHAVIORAL', 'LEADERSHIP'),
  difficulty_level ENUM('JUNIOR', 'MID', 'SENIOR', 'STAFF'),
  resume_id BIGINT,
  is_active BOOLEAN DEFAULT TRUE,
  last_triggered TIMESTAMP NULL,
  next_trigger TIMESTAMP NOT NULL,
  INDEX idx_next_trigger (next_trigger, is_active),
  INDEX idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
