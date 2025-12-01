"""
Round-by-Round Attack Generation
LLM analyzes current system state and discovers new anti-patterns to exploit
"""
from typing import Dict, Any, List
from infrastructure.clients.grok_client import grok_client
import json


class RoundAttackGenerator:
    """
    Generates attack options dynamically based on current system state
    LLM acts as Chaos Monkey discovering vulnerabilities after each fix
    """

    async def generate_attacks_for_round(
        self,
        round_number: int,
        scenario: Dict[str, Any],
        system_state: Dict[str, Any],
        deployed_fixes: List[str],
        attack_history: Dict[str, int]
    ) -> Dict[str, Any]:
        """
        LLM analyzes system and generates 5 contextual attacks

        Args:
            round_number: Current round (1-5)
            scenario: Original scenario (story context)
            system_state: Current architecture (after previous fixes)
            deployed_fixes: List of fixes already deployed (e.g., ["circuit_breaker", "rate_limiting"])
            attack_history: How many times each attack has been used

        Returns:
            {
                "discovered_anti_patterns": ["async_messaging_missing", "no_bulkhead"],
                "chaos_monkey_analysis": "I see what they don't see...",
                "attack_options": [
                    {
                        "key": "event_loop_starvation",
                        "title": "Event Loop Starvation",
                        "description": "Heavy CPU task blocks async event loop",
                        "base_severity": "100ms blocking task",
                        "chaos_monkey_observation": "They added async messaging but forgot the event loop can still block..."
                    }
                ]
            }
        """
        system_prompt = f"""You are the CHAOS MONKEY 😈 analyzing a system after Round {round_number - 1}.

Your role:
1. Analyze what fixes were deployed
2. Discover NEW anti-patterns/vulnerabilities that remain
3. Generate 5 creative attacks exploiting these weaknesses

**Character voice:**
- Mischievous devil who delights in finding flaws
- Observant: "They fixed X, but I spot Y..."
- Educational: Explain WHY the new vulnerability exists
- Not malicious, just revealing truth

**Analysis mindset:**
- Each fix introduces NEW problems (realistic engineering)
- Example: Circuit breaker added → but no bulkhead isolation (one circuit affects all)
- Example: Rate limiter added → but no prioritization (important requests get throttled too)
- Example: Async messaging added → but event loop can still block

Output JSON only."""

        # Describe what's been fixed
        fixes_description = "None yet - original naive architecture" if not deployed_fixes else ", ".join(deployed_fixes)

        # Describe current system
        services = system_state.get("services", [])
        service_count = len([s for s in services if s.get("type") == "microservice"])

        user_prompt = f"""**CURRENT SYSTEM STATE (Round {round_number}):**

**Deployed Fixes:**
{fixes_description}

**System Architecture:**
- Services: {service_count} microservices
- Current state: {json.dumps(system_state, indent=2)[:500]}...

**Your Mission (Chaos Monkey 😈):**
Analyze this system and discover what vulnerabilities REMAIN or were INTRODUCED by previous fixes.

Generate:

1. **discovered_anti_patterns** (array of strings): 2-3 NEW anti-patterns you discovered
   - Focus on what's NOT protected yet
   - Or new problems introduced by fixes
   - Examples: "no_bulkhead_isolation", "event_loop_blocking", "priority_inversion"

2. **chaos_monkey_analysis** (string, 2-3 sentences): Your mischievous observation
   - "They fixed the circuit breaker, but I notice..."
   - Show what they MISSED
   - Playful, devilish tone

3. **attack_options** (array, exactly 5 attacks): Creative ways to exploit the discovered anti-patterns
   - Each attack should be DIFFERENT from previous rounds (be creative!)
   - Format:
   ```
   {{
     "key": "snake_case_name",
     "title": "2-4 Words",
     "description": "5-10 words with numbers",
     "base_severity": "numeric value with unit",
     "chaos_monkey_observation": "1-2 sentences explaining why this works"
   }}
   ```

**CRITICAL REQUIREMENTS:**
- Round {round_number} attacks must be DIFFERENT from Round 1
- Don't just repeat "concurrent_writes" - be creative!
- Focus on CURRENT vulnerabilities (after fixes deployed)
- Show how fixes introduced NEW problems

**Example for Round 2 (after circuit_breaker deployed):**
```json
{{
  "discovered_anti_patterns": ["no_bulkhead_isolation", "shared_thread_pool"],
  "chaos_monkey_analysis": "They added circuit breakers to prevent cascading failures. Smart! But I notice... all circuits share the SAME thread pool. One misbehaving endpoint can starve the entire system. Delicious.",
  "attack_options": [
    {{
      "key": "thread_pool_exhaustion",
      "title": "Thread Pool Exhaustion",
      "description": "One slow endpoint consumes all 200 threads",
      "base_severity": "200 threads",
      "chaos_monkey_observation": "Circuit breakers won't help if all threads are stuck before the circuit even trips..."
    }}
  ]
}}
```

Output ONLY valid JSON (no markdown, no code blocks):
"""

        print(f"\n🎯 ROUND {round_number} - Generating attacks based on current system state...")
        print(f"   Deployed fixes: {deployed_fixes}")

        try:
            response = await grok_client.chat(
                prompt=user_prompt,
                system_prompt=system_prompt,
                response_format={"type": "json_object"},
                temperature=0.6,  # Creative but coherent
                max_tokens=800
            )

            print(f"   ✅ LLM Response: {response[:200]}...")

            # Parse JSON
            result = json.loads(response)

            # Validate structure
            if not all(k in result for k in ["discovered_anti_patterns", "chaos_monkey_analysis", "attack_options"]):
                print(f"   ⚠️ Missing required fields, using fallback")
                return self._get_fallback_attacks(round_number, deployed_fixes)

            if not isinstance(result["attack_options"], list) or len(result["attack_options"]) < 3:
                print(f"   ⚠️ Insufficient attacks, using fallback")
                return self._get_fallback_attacks(round_number, deployed_fixes)

            print(f"   ✅ Discovered anti-patterns: {result['discovered_anti_patterns']}")
            print(f"   ✅ Generated {len(result['attack_options'])} attacks")

            return result

        except Exception as e:
            print(f"   ❌ Attack generation error: {e}")
            return self._get_fallback_attacks(round_number, deployed_fixes)

    def _get_fallback_attacks(self, round_number: int, deployed_fixes: List[str]) -> Dict[str, Any]:
        """
        Fallback attacks if LLM fails
        Still contextual to round number
        """
        # Round-specific fallbacks
        round_fallbacks = {
            1: {
                "discovered_anti_patterns": ["shared_database", "no_connection_pooling"],
                "chaos_monkey_analysis": "The naive architect built a simple system. Too simple. All services share one database. Let's see what happens...",
                "attack_options": [
                    {"key": "concurrent_writes", "title": "Concurrent Writes", "description": "1000 simultaneous writes", "base_severity": "1000 writes/sec", "chaos_monkey_observation": "All services write to the same table. Lock cascade incoming..."},
                    {"key": "deadlock_scenario", "title": "Deadlock", "description": "Circular lock dependencies", "base_severity": "500 transactions", "chaos_monkey_observation": "Classic embrace of death."},
                    {"key": "connection_leak", "title": "Connection Leak", "description": "Gradual connection pool exhaustion", "base_severity": "100 connections", "chaos_monkey_observation": "No cleanup, connections pile up..."},
                    {"key": "table_scan", "title": "Full Table Scan", "description": "Query without index on 10M rows", "base_severity": "10M rows", "chaos_monkey_observation": "Missing index means every query scans everything."},
                    {"key": "lock_timeout", "title": "Lock Timeout", "description": "30s lock wait cascades", "base_severity": "30s timeout", "chaos_monkey_observation": "One slow transaction blocks everyone."}
                ]
            },
            2: {
                "discovered_anti_patterns": ["no_bulkhead_isolation", "shared_thread_pool"],
                "chaos_monkey_analysis": "They fixed the database. But now all services share the same thread pool. One bad endpoint can starve the system.",
                "attack_options": [
                    {"key": "thread_starvation", "title": "Thread Starvation", "description": "One endpoint consumes 200 threads", "base_severity": "200 threads", "chaos_monkey_observation": "No bulkhead means one leak sinks the ship."},
                    {"key": "cpu_spike", "title": "CPU Spike", "description": "Regex catastrophic backtracking", "base_severity": "100% CPU", "chaos_monkey_observation": "One regex can freeze all workers."},
                    {"key": "memory_leak", "title": "Memory Leak", "description": "Gradual heap exhaustion", "base_severity": "2GB heap", "chaos_monkey_observation": "No isolation means one leak affects all."},
                    {"key": "slow_dependency", "title": "Slow Dependency", "description": "External API takes 30s", "base_severity": "30s latency", "chaos_monkey_observation": "Synchronous calls = everyone waits."},
                    {"key": "unbounded_queue", "title": "Unbounded Queue", "description": "Message queue grows to 1M items", "base_severity": "1M messages", "chaos_monkey_observation": "No backpressure = memory explosion."}
                ]
            }
        }

        # Return appropriate fallback
        return round_fallbacks.get(round_number, round_fallbacks[1])


# Singleton instance
round_attack_generator = RoundAttackGenerator()
