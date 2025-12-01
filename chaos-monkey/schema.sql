-- Chaos Monkey Database Schema
-- PostgreSQL 15+

-- Scenarios (like problems table in code-raider)
CREATE TABLE IF NOT EXISTS chaos_scenarios (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- Story elements
  story_title TEXT NOT NULL,
  story_theme TEXT NOT NULL CHECK (story_theme IN ('e-commerce', 'streaming', 'gaming', 'fintech', 'social')),
  story_intro TEXT NOT NULL,
  story_stakes TEXT NOT NULL,

  -- Technical elements
  target_lesson TEXT NOT NULL,
  anti_pattern TEXT NOT NULL,
  difficulty TEXT NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
  initial_architecture JSONB NOT NULL,

  -- Metadata
  created_at TIMESTAMP DEFAULT NOW(),
  created_by UUID
);

-- Game Sessions (Redis during play → PostgreSQL on completion)
CREATE TABLE IF NOT EXISTS chaos_sessions (
  id UUID PRIMARY KEY,
  user_id UUID,
  scenario_id UUID REFERENCES chaos_scenarios(id) ON DELETE CASCADE,
  current_round INT DEFAULT 1,
  max_rounds INT DEFAULT 5,
  is_completed BOOLEAN DEFAULT FALSE,
  final_sla JSONB,
  created_at TIMESTAMP DEFAULT NOW(),
  completed_at TIMESTAMP
);

-- Game Rounds (attack + defense history)
CREATE TABLE IF NOT EXISTS chaos_rounds (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id UUID REFERENCES chaos_sessions(id) ON DELETE CASCADE,
  round_number INT NOT NULL,

  -- User's chaos attack
  attack_vector TEXT NOT NULL,
  attack_params TEXT,

  -- Simulation results
  sla_before JSONB NOT NULL,
  sla_after_attack JSONB NOT NULL,
  sla_after_fix JSONB NOT NULL,

  -- Architect's response
  fix_deployed TEXT NOT NULL,
  fix_explanation TEXT NOT NULL,
  fix_config JSONB,

  -- Story progression
  chaos_story TEXT NOT NULL,
  architect_story TEXT NOT NULL,
  ascii_animation TEXT NOT NULL,

  -- TTS audio URLs
  chaos_audio_url TEXT,
  architect_audio_url TEXT,

  created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_sessions_user ON chaos_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_scenario ON chaos_sessions(scenario_id);
CREATE INDEX IF NOT EXISTS idx_rounds_session ON chaos_rounds(session_id);
CREATE INDEX IF NOT EXISTS idx_scenarios_difficulty ON chaos_scenarios(difficulty);
CREATE INDEX IF NOT EXISTS idx_scenarios_theme ON chaos_scenarios(story_theme);

-- Comments for documentation
COMMENT ON TABLE chaos_scenarios IS 'System architecture scenarios with story narratives';
COMMENT ON TABLE chaos_sessions IS 'Game sessions - active in Redis, completed in PostgreSQL';
COMMENT ON TABLE chaos_rounds IS 'Individual rounds showing attack/defense progression';
