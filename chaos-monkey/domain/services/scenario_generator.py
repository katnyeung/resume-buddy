"""
Scenario Generator - Converts real incidents into story-driven architecture scenarios
Uses Grok LLM to generate 3 variations with different themes
"""
import json
from typing import List, Dict, Any
from infrastructure.clients.grok_client import grok_client


class ScenarioGenerator:
    """
    Generates chaos engineering scenarios from real-world incidents
    """

    async def generate_from_incident(
        self,
        incident_text: str,
        story_theme_hint: str = ""
    ) -> List[Dict[str, Any]]:
        """
        Takes real incident description + optional theme hint
        Returns 3 story variations with complete scenario data

        Args:
            incident_text: Real-world incident description (postmortem, blog, etc.)
            story_theme_hint: Optional theme constraint (e.g., "e-commerce", "Black Friday")

        Returns:
            List of 3 scenario variations with all required fields
        """
        system_prompt = """You are an expert chaos engineering scenario designer.
Your job is to convert real-world system failures into immersive, story-driven learning scenarios."""

        user_prompt = self._build_generation_prompt(incident_text, story_theme_hint)

        # Call Grok with JSON response format
        response_text = await grok_client.chat(
            prompt=user_prompt,
            system_prompt=system_prompt,
            response_format={"type": "json_object"},
            temperature=0.8,  # Higher for creative stories
            max_tokens=4000  # Large for 3 full scenarios
        )

        # Parse JSON response
        try:
            result = json.loads(response_text)
            variations = result.get("variations", [])

            # Validate each variation has required fields
            for v in variations:
                self._validate_variation(v)

            return variations

        except (json.JSONDecodeError, KeyError, ValueError) as e:
            print(f"❌ Failed to parse LLM response: {e}")
            print(f"Response: {response_text[:500]}...")
            raise ValueError(f"Invalid LLM response format: {e}")

    def _build_generation_prompt(self, incident_text: str, story_theme_hint: str) -> str:
        """
        Constructs detailed prompt for Grok LLM
        """
        theme_instruction = ""
        if story_theme_hint:
            theme_instruction = f"\n\nIMPORTANT: ALL 3 variations MUST respect this theme hint: \"{story_theme_hint}\"\nDo NOT ignore this constraint. All stories must incorporate this theme/setting."

        return f"""
Given this REAL-WORLD INCIDENT, create 3 immersive story-driven chaos engineering scenarios.

**CHARACTER ROLEPLAY:**
You will act as TWO characters designing this scenario together:

1. **THE NAIVE ARCHITECT** 🏗️
   - Junior engineer who built the initial system
   - Made well-intentioned but flawed architectural decisions
   - Explains their reasoning: "Why complicate things? One database is simpler!"
   - Confident but about to learn a hard lesson

2. **THE CHAOS MONKEY** 😈
   - Mischievous devil who reveals system weaknesses
   - Not malicious, but delights in exposing vulnerabilities
   - Creates the 5 attack options (MC questions)
   - Observes: "I see what they don't see... that shared database is a ticking time bomb."

**THE SCENARIO CREATION DIALOGUE:**

Round 1: Architect builds initial system (naive but confident)
→ Chaos Monkey grins and creates 5 ways to exploit the flaw
→ Attack happens, system crashes
→ Architect learns and deploys better fix with explanation

REAL INCIDENT:
{incident_text}
{theme_instruction}

For EACH of the 3 variations, generate:

1. **story_title** (string): Catchy title (e.g., "The Black Friday Meltdown", "The Streaming Apocalypse")

2. **story_theme** (string): One of ["e-commerce", "streaming", "gaming", "fintech", "social"]

3. **story_intro** (string): SHORT story setup (150-200 words MAX). Structure:

   **Use this EXACT narrative format:**

   Paragraph 1 - THE NAIVE ARCHITECT BUILDS (calm scene):
   "It's Black Friday 2024. Junior architect Alex just deployed MegaMart's new platform. Three microservices, one PostgreSQL database. 'Why complicate things?' Alex thinks proudly. 'Shared database means simpler deployment, easier transactions. The seniors overthink everything.'"

   Paragraph 2 - THE CHAOS MONKEY OBSERVES (you enter):
   "But YOU... you're the Chaos Monkey 😈 A mischievous little devil who spots what Alex doesn't see. That shared database? All three services writing to the same tables. One surge and those database locks will cascade like dominoes. You grin, ready to reveal the flaw."

   Paragraph 3 - THE STAKES (what's at risk):
   "Black Friday revenue: $500k/hour. Customer trust on the line. Let's see if Alex's 'simple' architecture can handle the chaos..."

   **CRITICAL REQUIREMENTS:**
   - Architect is naive, well-intentioned, makes it sound reasonable
   - Chaos Monkey is YOU (second-person: "you grin", "you spot")
   - Keep architect quote SHORT (1 sentence max)
   - Show the flaw from Chaos Monkey's perspective
   - Stakes in paragraph 3

4. **story_stakes** (string): What's at risk (e.g., "$500k/hour revenue loss, brand reputation, customer trust")

5. **target_lesson** (string): The architectural pattern to teach. Choose ONE from:
   - "database_per_service" (microservices sharing database)
   - "circuit_breaker" (cascading failures)
   - "rate_limiting" (no traffic control)
   - "async_messaging" (synchronous microservices)
   - "redundancy" (single point of failure)
   - "saga_pattern" (distributed transactions)
   - "bulkhead_pattern" (resource isolation)
   - "cqrs" (read/write separation)

6. **anti_pattern** (string): The initial architectural flaw. Choose ONE from:
   - "shared_database"
   - "no_circuit_breaker"
   - "no_rate_limiting"
   - "sync_microservices"
   - "single_point_failure"
   - "no_retry_logic"
   - "tight_coupling"

7. **difficulty** (string): One of ["EASY", "MEDIUM", "HARD"]
   - EASY: 1-2 services, single pattern (circuit breaker only)
   - MEDIUM: 3-4 services, 2 patterns (DB split + message queue)
   - HARD: 5+ services, complex trade-offs (CAP theorem, eventual consistency)

8. **initial_architecture** (JSON object): The NAIVE architect's initial system design.

   **Design principle:** This is what the junior architect built - simple, but flawed.

   Format:
```json
{{
  "architect_reasoning": "Why complicate things? One database keeps deployment simple.",
  "services": [
    {{"id": "order", "name": "Order Service", "type": "microservice"}},
    {{"id": "payment", "name": "Payment Service", "type": "microservice"}},
    {{"id": "db", "name": "PostgreSQL", "type": "database"}}
  ],
  "connections": [
    {{"from": "order", "to": "db", "latency": 50}},
    {{"from": "payment", "to": "db", "latency": 50}}
  ],
  "sla_baseline": {{
    "uptime": 99.9,
    "latency": 200,
    "error_rate": 0.1,
    "requests_per_second": 1000
  }}
}}
```

   **Include:**
   - `architect_reasoning`: 1-sentence quote explaining why they built it this way
   - Services that demonstrate the anti-pattern (shared DB, sync calls, etc.)
   - Baseline SLA (optimistic, before chaos)

9. **attack_options** (JSON array): THE CHAOS MONKEY'S 5 ways to exploit the flaw.

   **Character voice:** These are created by YOU (the Chaos Monkey 😈) after observing the architect's naive design.

   Format:
```json
[
  {{
    "key": "concurrent_writes",
    "title": "Concurrent Writes",
    "description": "1000 simultaneous write operations to shared database",
    "base_severity": "1000 writes/sec",
    "chaos_monkey_observation": "All three services write to the same table? Let's see what happens when they all try at once..."
  }},
  {{
    "key": "deadlock_scenario",
    "title": "Deadlock Scenario",
    "description": "Circular lock dependencies in database",
    "base_severity": "500 transactions",
    "chaos_monkey_observation": "Order locks payment row, payment locks order row. A classic embrace of death."
  }}
]
```

   **Requirements:**
   - Generate 5 diverse attack types (load, latency, crashes, data races, dependencies)
   - Each attack DIRECTLY exploits the anti_pattern the architect introduced
   - Use snake_case for "key" (e.g., traffic_surge, service_crash)
   - Title: 2-4 words
   - Description: 5-10 words with realistic numbers
   - base_severity: numeric intensity (used for escalation: 2x, 4x, 8x)
   - chaos_monkey_observation: 1-2 sentences from Chaos Monkey POV ("Let's see what happens when...")

   **Examples by anti-pattern:**

   shared_database → concurrent_writes, deadlock_scenario, table_lock, connection_exhaustion, index_missing
   no_rate_limiting → traffic_surge, bot_attack, api_abuse, ddos_simulation, resource_exhaustion
   no_circuit_breaker → latency_spike, timeout_cascade, thread_exhaustion, retry_storm, memory_leak

REQUIREMENTS FOR THE 3 VARIATIONS:
- Variation 1: Dramatic stakes, urgent timeline (Black Friday, peak streaming)
- Variation 2: Technical focus, professional tone (engineering review)
- Variation 3: Viral/social angle, community impact (gaming event, social media spike)

Return ONLY valid JSON in this exact format (no markdown, no code blocks):
{{
  "variations": [
    {{
      "story_title": "...",
      "story_theme": "e-commerce",
      "story_intro": "...",
      "story_stakes": "...",
      "target_lesson": "database_per_service",
      "anti_pattern": "shared_database",
      "difficulty": "MEDIUM",
      "initial_architecture": {{...}},
      "attack_options": [
        {{
          "key": "concurrent_writes",
          "title": "Concurrent Writes",
          "description": "1000 simultaneous write operations",
          "base_severity": "1000 writes/sec"
        }},
        ... (4 more attacks)
      ]
    }},
    ... (2 more variations)
  ]
}}
"""

    def _validate_variation(self, variation: Dict[str, Any]):
        """
        Validates that variation has all required fields
        Raises ValueError if missing/invalid
        """
        required_fields = [
            "story_title",
            "story_theme",
            "story_intro",
            "story_stakes",
            "target_lesson",
            "anti_pattern",
            "difficulty",
            "initial_architecture",
            "attack_options"
        ]

        for field in required_fields:
            if field not in variation:
                raise ValueError(f"Missing required field: {field}")

        # Validate story_theme
        valid_themes = ["e-commerce", "streaming", "gaming", "fintech", "social"]
        if variation["story_theme"] not in valid_themes:
            raise ValueError(f"Invalid story_theme: {variation['story_theme']}")

        # Validate difficulty
        valid_difficulties = ["EASY", "MEDIUM", "HARD"]
        if variation["difficulty"] not in valid_difficulties:
            raise ValueError(f"Invalid difficulty: {variation['difficulty']}")

        # Validate initial_architecture has required keys
        arch = variation["initial_architecture"]
        if not isinstance(arch, dict):
            raise ValueError("initial_architecture must be a JSON object")

        if "services" not in arch or "connections" not in arch or "sla_baseline" not in arch:
            raise ValueError("initial_architecture missing required keys (services/connections/sla_baseline)")

        # Validate attack_options
        attacks = variation["attack_options"]
        if not isinstance(attacks, list) or len(attacks) < 3:
            raise ValueError("attack_options must be array with at least 3 attacks")

        required_attack_fields = ["key", "title", "description"]
        for attack in attacks:
            if not all(k in attack for k in required_attack_fields):
                raise ValueError(f"attack_options missing required fields: {attack}")
            # chaos_monkey_observation is optional but recommended


# Singleton instance
scenario_generator = ScenarioGenerator()
