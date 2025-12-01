"""
Two-Phase Round Orchestrator
Phase 1: Generate 5 attack options (lightweight)
Phase 2: Execute selected attack with full outcome
"""
from typing import Dict, Any, List
from infrastructure.clients.grok_client import grok_client
import json
import time


class RoundOrchestrator:
    """
    Orchestrates rounds in TWO LLM calls:
    1. Generate 5 attack options (no full outcomes)
    2. Execute selected attack (chaos story + fix + architect story + diagram)
    """

    async def orchestrate_round(
        self,
        round_number: int,
        scenario: Dict[str, Any],
        system_state: Dict[str, Any],
        deployed_fixes: List[str],
        attack_history: Dict[str, int],
        selected_attack: str = None,
        attack_params: str = ""
    ) -> Dict[str, Any]:
        """
        Single LLM call when user clicks "UNLEASH CHAOS"

        Returns BOTH:
        1. Outcome for selected attack (story, fix, diagram)
        2. 5 attack suggestions for NEXT round

        Special case: If selected_attack is None (Round 1 start), just generate initial options
        """
        if selected_attack is None:
            # Round 1 initialization: Just generate attack options
            return await self._generate_initial_attacks(
                round_number, scenario, system_state, deployed_fixes, attack_history
            )
        else:
            # User clicked "UNLEASH CHAOS": Execute attack + generate next round's options
            return await self._execute_and_prepare_next(
                round_number, scenario, system_state, deployed_fixes,
                selected_attack, attack_params, attack_history
            )

    async def _generate_initial_attacks(
        self,
        round_number: int,
        scenario: Dict[str, Any],
        system_state: Dict[str, Any],
        deployed_fixes: List[str],
        attack_history: Dict[str, int]
    ) -> Dict[str, Any]:
        """Generate 5 story-driven misfortune events (Round 1 only)"""

        story_theme = scenario.get("story_theme", "technology")
        story_title = scenario.get("story_title", "System Crisis")

        system_prompt = """You are the CHAOS MONKEY 😈 - a mischievous storyteller who sees vulnerabilities.

You DON'T "inject" or "attack" - you FORESEE unfortunate events that COULD happen naturally!

Generate 5 STORY-DRIVEN misfortune scenarios. Each should be a dramatic "what if" event:

❌ BAD (technical injection):
- "Inject 5000 fake score updates/sec"
- "Flood matchmaker with 2000 requests"
- "Spawn 800 rogue connections"

✅ GOOD (story-driven misfortune):
- "A viral TikTok streamer goes live, and 50,000 fans flood the leaderboard at once!"
- "The grand finals begin - every player hits 'Find Match' simultaneously!"
- "A celebrity tweet sends the app trending, crashing the login servers!"
- "Prime time hits and parents nationwide log in with their kids!"
- "A regional ISP hiccup causes 10,000 reconnection storms!"

Make it DRAMATIC, REALISTIC, and tied to the scenario's theme. Include specific numbers for impact.

Output JSON:
{
  "discovered_anti_patterns": ["pattern1", "pattern2"],
  "chaos_monkey_analysis": "Your mischievous observation about the system's weakness (2-3 sentences)",
  "attack_options": [
    {
      "key": "viral_surge",
      "title": "Viral Celebrity Moment",
      "description": "A famous streamer tweets 'Playing this NOW!' - 80,000 users flood in within 60 seconds!",
      "base_severity": "80,000 concurrent users",
      "chaos_monkey_observation": "That single database won't handle this stampede..."
    },
    ... (4 more story-driven events)
  ]
}"""

        fixes_desc = "None" if not deployed_fixes else ", ".join(deployed_fixes)

        user_prompt = f"""**Scenario:** {story_title}
**Theme:** {story_theme}
**Round:** {round_number}

**Deployed Fixes:** {fixes_desc}

**Current System Architecture:**
{json.dumps(system_state, indent=2)[:500]}

As the Chaos Monkey, you see this system's weaknesses. Generate 5 STORY-DRIVEN misfortune events that could naturally happen and expose these flaws.

Each event should:
1. Be a realistic scenario (viral moment, peak traffic, celebrity mention, infrastructure hiccup)
2. Include specific dramatic numbers (50,000 users, 10,000 requests/sec)
3. Feel like bad luck, not a hacker attack
4. Match the {story_theme} theme

Output JSON only."""

        print(f"   ⏳ Generating attack options...")
        llm_start = time.time()

        try:
            response = await grok_client.chat(
                prompt=user_prompt,
                system_prompt=system_prompt,
                response_format={"type": "json_object"},
                temperature=0.6,
                max_tokens=1500  # Increased for story-driven descriptions
            )
            llm_elapsed = time.time() - llm_start
            print(f"   ⏱️  Grok response time: {llm_elapsed:.2f}s")

            try:
                result = json.loads(response)
            except json.JSONDecodeError as je:
                print(f"   ❌ JSON parse error: {je}")
                print(f"   📄 Raw response (last 200 chars): ...{response[-200:]}")
                raise
            attack_options = result.get("attack_options", [])

            # Validate
            valid_attacks = []
            for attack in attack_options:
                if all(k in attack for k in ["key", "title", "description", "base_severity"]):
                    valid_attacks.append(attack)

            if len(valid_attacks) > 5:
                valid_attacks = valid_attacks[:5]

            result["attack_options"] = valid_attacks
            print(f"   ✅ Generated {len(valid_attacks)} attack options")
            return result

        except Exception as e:
            print(f"   ❌ Attack generation error: {e}")
            return {
                "discovered_anti_patterns": ["shared_database"],
                "chaos_monkey_analysis": "Fallback analysis.",
                "attack_options": [
                    {"key": "concurrent_writes", "title": "Concurrent Writes",
                     "description": "1000 writes", "base_severity": "1000 writes/sec",
                     "chaos_monkey_observation": "Fallback attack"}
                ]
            }

    async def _execute_and_prepare_next(
        self,
        round_number: int,
        scenario: Dict[str, Any],
        system_state: Dict[str, Any],
        deployed_fixes: List[str],
        selected_attack: str,
        attack_params: str,
        attack_history: Dict[str, int]
    ) -> Dict[str, Any]:
        """Execute selected misfortune AND generate next round's story events"""

        story_theme = scenario.get("story_theme", "technology")
        story_title = scenario.get("story_title", "System Crisis")

        system_prompt = f"""You are the narrator of a CHAOS ENGINEERING DRAMA - Round {round_number}.

The misfortune event has struck! Now tell TWO stories:

1. **THE INCIDENT (chaos_story)**: Dramatic narration of what happened when the event struck.
   - Write like a news reporter covering a live crisis
   - Include specific metrics (latency spiked to 8500ms, 28% errors)
   - Show user impact (players rage-quitting, Twitter exploding)
   - 100 words, dramatic but realistic

2. **EMERGENCY RESPONSE (architect_story)**: The lead architect's heroic fix.
   - First-person perspective from the architect
   - Show the "aha moment" of identifying the root cause
   - Explain the fix in simple terms
   - End with metrics recovered and lessons learned
   - 100 words, professional but triumphant

Also generate 5 NEW story-driven misfortune events for Round {round_number + 1}.
These should target vulnerabilities that STILL EXIST after the fix, or NEW weaknesses the fix introduced.

Remember: Events are MISFORTUNES (viral moments, traffic spikes, infrastructure hiccups) NOT technical attacks!

Output comprehensive JSON."""

        attack_count = attack_history.get(selected_attack, 0) + 1

        user_prompt = f"""**Scenario:** {story_title}
**Theme:** {story_theme}
**Round:** {round_number}

**Current System:**
{json.dumps(system_state, indent=2)[:500]}

**Deployed Fixes:** {', '.join(deployed_fixes) if deployed_fixes else 'None'}
**Misfortune Event:** {selected_attack}
**Event Details:** {attack_params if attack_params else 'Standard severity'}

The misfortune has struck! Generate the dramatic outcome.

Return JSON:
{{
  "sla_after_attack": {{"uptime": 35.0, "latency": 8500, "error_rate": 28.0}},
  "failures": [{{"service": "db", "type": "overload", "severity": "critical", "message": "Connection pool exhausted"}}],
  "chaos_story": "Dramatic 100-word narration of the incident unfolding...",
  "fix_deployed": "database_per_service",
  "fix_name": "Database per Service",
  "fix_explanation": "What the fix does technically",
  "fix_why": "Why this solves the root cause",
  "fix_real_world": "Netflix, Uber use this pattern",
  "sla_after_fix": {{"uptime": 99.9, "latency": 160, "error_rate": 0.0}},
  "architect_story": "First-person 100-word story from the architect deploying the fix...",
  "ascii_diagram": "ASCII diagram (15 lines, 60 chars) showing the NEW architecture after fix",
  "new_system_state": {{updated system state with the fix applied}},
  "next_round_attacks": [
    {{
      "key": "prime_time_surge",
      "title": "Prime Time Tsunami",
      "description": "It's 8 PM on a Friday - every teenager in the country logs in simultaneously!",
      "base_severity": "150,000 concurrent users",
      "chaos_monkey_observation": "The new per-service DBs haven't been load tested yet..."
    }},
    ... (4 more STORY-DRIVEN misfortune events for Round {round_number + 1})
  ]
}}

Output JSON only."""

        print(f"   ⏳ Executing attack + generating next round...")
        llm_start = time.time()

        try:
            response = await grok_client.chat(
                prompt=user_prompt,
                system_prompt=system_prompt,
                response_format={"type": "json_object"},
                temperature=0.7,
                max_tokens=3000
            )
            llm_elapsed = time.time() - llm_start
            print(f"   ⏱️  Grok response time: {llm_elapsed:.2f}s (execute + next attacks)")

            parse_start = time.time()
            try:
                result = json.loads(response)
            except json.JSONDecodeError as je:
                print(f"   ❌ JSON parse error: {je}")
                print(f"   📄 Raw response (last 300 chars): ...{response[-300:]}")
                raise
            parse_elapsed = time.time() - parse_start
            print(f"   ⏱️  JSON parse time: {parse_elapsed:.3f}s")
            print(f"   ✅ Round complete, next attacks ready")
            return result

        except Exception as e:
            print(f"   ❌ Execution error: {e}")
            return {
                "sla_after_attack": {"uptime": 50.0, "latency": 5000, "error_rate": 15.0},
                "failures": [{"service": "system", "type": "fallback", "severity": "high", "message": "Fallback"}],
                "chaos_story": "Fallback chaos story.",
                "fix_deployed": "generic_fix",
                "fix_name": "Generic Fix",
                "fix_explanation": "Fallback fix.",
                "fix_why": "Fallback reason.",
                "fix_real_world": "Fallback example.",
                "sla_after_fix": {"uptime": 99.0, "latency": 250, "error_rate": 0.5},
                "architect_story": "Fallback architect story.",
                "ascii_diagram": "┌────────────┐\n│   System   │\n└────────────┘",
                "new_system_state": system_state,
                "next_round_attacks": []
            }


# Singleton
round_orchestrator = RoundOrchestrator()
