-- Code Raider Database Schema
-- PostgreSQL (Neon) schema for story-based coding game

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Problems table (admin creates from LeetCode)
CREATE TABLE IF NOT EXISTS problems (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    algorithm_type TEXT NOT NULL,  -- 'dynamic_programming', 'greedy', 'graph', etc.
    difficulty TEXT NOT NULL DEFAULT 'MEDIUM',  -- 'EASY', 'MEDIUM', 'HARD'
    test_cases JSONB NOT NULL,  -- [{"input": [...], "expected": ...}, ...]
    story_seed TEXT NOT NULL,  -- Theme for story generation (dungeon, heist, space, etc.)
    function_signature TEXT,  -- e.g., "def rob(nums: List[int]) -> int:"
    created_at TIMESTAMP DEFAULT NOW(),
    created_by UUID  -- Admin user ID
);

-- Game sessions (persisted when complete, active sessions in Redis)
CREATE TABLE IF NOT EXISTS game_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL,
    problem_id UUID REFERENCES problems(id) ON DELETE CASCADE,
    session_type TEXT DEFAULT 'DUEL',  -- Always 'DUEL' for MVP
    current_round INT DEFAULT 1,
    max_rounds INT DEFAULT 5,
    is_completed BOOLEAN DEFAULT FALSE,
    final_score INT,  -- Percentage of test cases passed
    created_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP
);

-- Session rounds (persisted when session complete)
CREATE TABLE IF NOT EXISTS session_rounds (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    session_id UUID REFERENCES game_sessions(id) ON DELETE CASCADE,
    round_number INT NOT NULL,
    user_input_text TEXT,  -- User's explanation/thought (e.g., "use dynamic programming")
    user_code TEXT,  -- User's code contribution
    ai_code TEXT,  -- AI-generated skeleton or suggested code
    combined_code TEXT,  -- Full executable code (ai_code + user_code)
    execution_result JSONB,  -- {"passed": 3, "total": 4, "results": [...]}
    story_text TEXT,  -- Story reaction to round results
    ai_message TEXT,  -- AI feedback/hints
    ascii_animation TEXT,  -- LLM-generated execution visualization
    emotion TEXT,  -- 'neutral', 'frustrated', 'impressed', 'encouraging', 'proud'
    tts_audio_url TEXT,  -- ElevenLabs audio URL (optional)
    created_at TIMESTAMP DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_sessions_user ON game_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_sessions_problem ON game_sessions(problem_id);
CREATE INDEX IF NOT EXISTS idx_sessions_completed ON game_sessions(is_completed);
CREATE INDEX IF NOT EXISTS idx_rounds_session ON session_rounds(session_id);
CREATE INDEX IF NOT EXISTS idx_problems_difficulty ON problems(difficulty);

-- Sample problem (House Robber - Dynamic Programming)
INSERT INTO problems (title, description, algorithm_type, difficulty, test_cases, story_seed, function_signature)
VALUES (
    'House Robber',
    'You are planning to rob houses along a street. Each house has a certain amount of money stashed. Adjacent houses have security systems connected, and it will automatically contact the police if two adjacent houses were broken into on the same night. Given an integer array nums representing the amount of money of each house, return the maximum amount of money you can rob tonight without alerting the police.',
    'dynamic_programming',
    'MEDIUM',
    '[
        {"input": [2,7,9,3,1], "expected": 12},
        {"input": [1,2,3,1], "expected": 4},
        {"input": [2,1,1,2], "expected": 4},
        {"input": [5,3,4,11,2], "expected": 16}
    ]'::jsonb,
    'Deep in the Obsidian Dungeon, your crew discovers 6 treasure vaults. Your scout warns: adjacent vaults have pressure-sensitive traps! Trigger one, and you lose everything. Time to think strategically, captain.',
    'def rob(nums: List[int]) -> int:'
) ON CONFLICT DO NOTHING;

COMMENT ON TABLE problems IS 'LeetCode-style problems with story themes';
COMMENT ON TABLE game_sessions IS 'Active and completed game sessions (active in Redis, completed in PostgreSQL)';
COMMENT ON TABLE session_rounds IS 'Round-by-round history of user input, AI responses, and execution results';
