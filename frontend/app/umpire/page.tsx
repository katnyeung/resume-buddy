'use client';

import { useState, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/contexts/AuthContext';
import {
  listProblems,
  getRandomProblem,
  createSession,
  getAlgorithmTypes,
  Problem,
} from '@/lib/api/umpire';

const DIFFICULTIES = ['EASY', 'MEDIUM', 'HARD'] as const;
const ROUND_INTERVALS = [
  { value: 'DAILY', label: 'Daily' },
  { value: 'EVERY_2_DAYS', label: 'Every 2 Days' },
  { value: 'WEEKLY', label: 'Weekly' },
];

export default function UmpirePracticePage() {
  const router = useRouter();
  const { user, token, loading: authLoading } = useAuth();
  const [problems, setProblems] = useState<Problem[]>([]);
  const [algorithmTypes, setAlgorithmTypes] = useState<string[]>([]);
  const [selectedProblem, setSelectedProblem] = useState<Problem | null>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);

  // Filters
  const [difficulty, setDifficulty] = useState<string>('');
  const [algorithmType, setAlgorithmType] = useState<string>('');

  // Session config
  const [scheduledDate, setScheduledDate] = useState(() => {
    const today = new Date();
    return today.toISOString().split('T')[0];
  });
  const [scheduledTime, setScheduledTime] = useState('09:00');
  const [totalRounds, setTotalRounds] = useState(3);
  const [roundInterval, setRoundInterval] = useState('DAILY');
  const [programmingLanguage, setProgrammingLanguage] = useState('python');

  useEffect(() => {
    if (authLoading) return; // Wait for auth to initialize

    if (!token) {
      router.push('/login');
      return;
    }
    loadData();
  }, [token, authLoading, router]);

  useEffect(() => {
    loadProblems();
  }, [difficulty, algorithmType]);

  async function loadData() {
    try {
      const [problemsData, types] = await Promise.all([
        listProblems({ limit: 50 }),
        getAlgorithmTypes(),
      ]);
      setProblems(problemsData.problems);
      setAlgorithmTypes(types);
    } catch (error) {
      console.error('Failed to load data:', error);
    } finally {
      setLoading(false);
    }
  }

  async function loadProblems() {
    try {
      const data = await listProblems({
        difficulty: difficulty || undefined,
        algorithm_type: algorithmType || undefined,
        limit: 50,
      });
      setProblems(data.problems);
    } catch (error) {
      console.error('Failed to load problems:', error);
    }
  }

  async function handleRandomProblem() {
    try {
      const problem = await getRandomProblem({
        difficulty: difficulty || undefined,
        algorithm_type: algorithmType || undefined,
      });
      setSelectedProblem(problem);
    } catch (error) {
      console.error('Failed to get random problem:', error);
    }
  }

  async function handleCreateSession() {
    if (!selectedProblem || !user) return;

    setCreating(true);
    try {
      const session = await createSession({
        user_id: user.id,
        problem_id: selectedProblem.id,
        programming_language: programmingLanguage,
        scheduled_date: scheduledDate,
        scheduled_time: scheduledTime + ':00',
        total_rounds: totalRounds,
        round_interval: roundInterval as 'DAILY' | 'EVERY_2_DAYS' | 'WEEKLY',
      });

      router.push(`/umpire/${session.id}`);
    } catch (error) {
      console.error('Failed to create session:', error);
      alert('Failed to create session. Please try again.');
    } finally {
      setCreating(false);
    }
  }

  if (loading) {
    return (
      <div className="min-h-screen bg-gray-900 text-white flex items-center justify-center">
        <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-500"></div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-900 text-white p-8">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-3xl font-bold mb-2">UMPIRE Pair Programming Practice</h1>
        <p className="text-gray-400 mb-8">
          Master coding interviews with the UMPIRE framework: Understand, Match, Plan, Implement, Review, Evaluate
        </p>

        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          {/* Problem Selection */}
          <div className="bg-gray-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4">Select a Problem</h2>

            {/* Filters */}
            <div className="grid grid-cols-2 gap-4 mb-4">
              <div>
                <label className="block text-sm text-gray-400 mb-1">Difficulty</label>
                <select
                  value={difficulty}
                  onChange={(e) => setDifficulty(e.target.value)}
                  className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                >
                  <option value="">All Difficulties</option>
                  {DIFFICULTIES.map((d) => (
                    <option key={d} value={d}>{d}</option>
                  ))}
                </select>
              </div>
              <div>
                <label className="block text-sm text-gray-400 mb-1">Algorithm Type</label>
                <select
                  value={algorithmType}
                  onChange={(e) => setAlgorithmType(e.target.value)}
                  className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                >
                  <option value="">All Types</option>
                  {algorithmTypes.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
              </div>
            </div>

            {/* Random Problem Button */}
            <button
              onClick={handleRandomProblem}
              className="w-full mb-4 bg-purple-600 hover:bg-purple-700 text-white font-medium py-2 px-4 rounded"
            >
              Pick Random Problem
            </button>

            {/* Problem List */}
            <div className="space-y-2 max-h-96 overflow-y-auto">
              {problems.map((problem) => (
                <div
                  key={problem.id}
                  onClick={() => setSelectedProblem(problem)}
                  className={`p-3 rounded cursor-pointer transition ${
                    selectedProblem?.id === problem.id
                      ? 'bg-blue-600'
                      : 'bg-gray-700 hover:bg-gray-600'
                  }`}
                >
                  <div className="flex justify-between items-center">
                    <span className="font-medium">{problem.title}</span>
                    <span className={`text-xs px-2 py-1 rounded ${
                      problem.difficulty === 'EASY' ? 'bg-green-600' :
                      problem.difficulty === 'MEDIUM' ? 'bg-yellow-600' :
                      'bg-red-600'
                    }`}>
                      {problem.difficulty}
                    </span>
                  </div>
                  <div className="text-sm text-gray-400 mt-1">
                    {problem.algorithm_type}
                  </div>
                </div>
              ))}
            </div>
          </div>

          {/* Session Configuration */}
          <div className="bg-gray-800 rounded-lg p-6">
            <h2 className="text-xl font-semibold mb-4">Configure Session</h2>

            {selectedProblem ? (
              <>
                {/* Selected Problem Preview */}
                <div className="bg-gray-700 rounded p-4 mb-6">
                  <h3 className="font-semibold text-lg">{selectedProblem.title}</h3>
                  <p className="text-gray-400 text-sm mt-2 line-clamp-3">
                    {selectedProblem.description}
                  </p>
                  <code className="block mt-2 text-green-400 text-sm">
                    {selectedProblem.function_signature}
                  </code>
                </div>

                {/* Configuration Form */}
                <div className="space-y-4">
                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Start Date</label>
                      <input
                        type="date"
                        value={scheduledDate}
                        onChange={(e) => setScheduledDate(e.target.value)}
                        className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                      />
                    </div>
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Start Time</label>
                      <input
                        type="time"
                        value={scheduledTime}
                        onChange={(e) => setScheduledTime(e.target.value)}
                        className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-4">
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Total Rounds</label>
                      <select
                        value={totalRounds}
                        onChange={(e) => setTotalRounds(Number(e.target.value))}
                        className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                      >
                        {[1, 2, 3, 4, 5].map((n) => (
                          <option key={n} value={n}>{n} round{n > 1 ? 's' : ''}</option>
                        ))}
                      </select>
                    </div>
                    <div>
                      <label className="block text-sm text-gray-400 mb-1">Round Interval</label>
                      <select
                        value={roundInterval}
                        onChange={(e) => setRoundInterval(e.target.value)}
                        className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                      >
                        {ROUND_INTERVALS.map((ri) => (
                          <option key={ri.value} value={ri.value}>{ri.label}</option>
                        ))}
                      </select>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm text-gray-400 mb-1">Programming Language</label>
                    <select
                      value={programmingLanguage}
                      onChange={(e) => setProgrammingLanguage(e.target.value)}
                      className="w-full bg-gray-700 border border-gray-600 rounded px-3 py-2"
                    >
                      <option value="python">Python</option>
                      <option value="java">Java</option>
                      <option value="typescript">TypeScript</option>
                    </select>
                  </div>

                  <button
                    onClick={handleCreateSession}
                    disabled={creating}
                    className="w-full bg-blue-600 hover:bg-blue-700 disabled:bg-gray-600 text-white font-medium py-3 px-4 rounded mt-4"
                  >
                    {creating ? 'Creating Session...' : 'Start UMPIRE Practice'}
                  </button>
                </div>
              </>
            ) : (
              <div className="text-center text-gray-400 py-12">
                <p>Select a problem from the list to configure your practice session.</p>
              </div>
            )}
          </div>
        </div>

        {/* UMPIRE Framework Info */}
        <div className="mt-8 bg-gray-800 rounded-lg p-6">
          <h2 className="text-xl font-semibold mb-4">The UMPIRE Framework</h2>
          <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
            {[
              { phase: 'U', name: 'Understand', desc: 'Read and clarify the problem' },
              { phase: 'M', name: 'Match', desc: 'Identify patterns and approaches' },
              { phase: 'P', name: 'Plan', desc: 'Write pseudocode' },
              { phase: 'I', name: 'Implement', desc: 'Write actual code' },
              { phase: 'R', name: 'Review', desc: 'Trace through test cases' },
              { phase: 'E', name: 'Evaluate', desc: 'Analyze complexity' },
            ].map((item, i) => (
              <div key={i} className="text-center p-4 bg-gray-700 rounded">
                <div className="text-2xl font-bold text-blue-400 mb-1">{item.phase}</div>
                <div className="font-medium">{item.name}</div>
                <div className="text-sm text-gray-400 mt-1">{item.desc}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}
