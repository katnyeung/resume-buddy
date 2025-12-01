"""
Story Generation Service
Generates narratives for chaos attacks and architect responses
"""
from typing import Dict, Any
from infrastructure.clients.grok_client import grok_client


class StoryGenerator:
    """
    Generates round-by-round narratives using Grok LLM
    Creates emotional story progression based on chaos results
    """

    async def generate_chaos_story(
        self,
        round_number: int,
        scenario: Dict[str, Any],
        attack_vector: str,
        chaos_result: Dict[str, Any]
    ) -> str:
        """
        Generate Chaos Monkey's mischief + incident story

        Args:
            round_number: Current round (1-5)
            scenario: Full scenario data
            attack_vector: Type of attack
            chaos_result: Simulation results

        Returns:
            Story text (incident narrative)
        """
        system_prompt = f"""You are narrating a story-driven chaos engineering game.

The user plays as THE CHAOS MONKEY 😈 - a mischievous little devil revealing system weaknesses through playful chaos. Not malicious, but cheeky and observant.
This is Round {round_number} of 5.

Generate a dramatic INCIDENT NARRATIVE (150-200 words):

Part 1 (2-3 sentences): The Chaos Monkey's mischief
- "You grin mischievously and unleash..."
- Playful, devilish tone (not evil, but cheeky)
- What chaos you cause

Part 2 (3-4 sentences): The incident unfolds
- System crashes, errors cascade
- Include specific SLA numbers
- Timeline: "Within 30 seconds...", "After 5 minutes..."

Part 3 (2-3 sentences): Human panic
- Engineers scramble, CEO screams
- Customers rage, revenue bleeds
- Emotional, dramatic

Tone: Story-driven, narrative, playful. NOT a technical report. The Chaos Monkey is a mischievous little devil, not evil or malicious.
Example: "You grin mischievously and unleash 1000 concurrent writes. The database buckles under the onslaught. Within seconds, locks cascade. Engineers' screens flash red. The CEO watches revenue plummet. You sit back, grinning at the chaos you've revealed..."
"""

        failures = chaos_result.get("failures", [])
        sla = chaos_result.get("sla_metrics", {})
        was_mitigated = chaos_result.get("was_mitigated", False)

        if was_mitigated:
            # Attack was mitigated - show system resilience!
            user_prompt = f"""
**Story Context:**
- Theme: {scenario.get('story_theme', '')}
- Stakes: {scenario.get('story_stakes', '')}
- Round: {round_number}/5

**Attack Vector:**
{attack_vector.replace('_', ' ').title()} (ATTEMPTED but MITIGATED!)

**System Resilience:**
- Uptime: {sla.get('uptime', 0):.1f}% (only minor degradation!)
- Latency: {sla.get('latency', 0):.0f}ms (2x normal, not 25x!)
- Error Rate: {sla.get('error_rate', 0):.1f}% (contained!)

**What Happened:**
{failures[0].get('message', '') if failures else 'Attack mitigated by resilience patterns'}

Write a RESILIENCE story (100-150 words):
- Part 1: You attempt the attack (same as before)
- Part 2: BUT the system handles it gracefully (previous fix works!)
- Part 3: Engineers NOTICE but don't panic (minor degradation, not disaster)

Example: "You orchestrate the same attack as before - 1000 concurrent writes. But this time, the system barely flinches. Each service writes to its OWN database - no locks, no cascade. Uptime stays at 98%, not 50%. Latency spikes to 400ms, not 5000ms. Engineers see the blip on their dashboards, but there's no panic. 'The database-per-service fix is working!' Raj smiles. The attack that once brought them to their knees is now... manageable."
"""
        else:
            # Normal attack - full impact
            user_prompt = f"""
**Story Context:**
- Theme: {scenario.get('story_theme', '')}
- Stakes: {scenario.get('story_stakes', '')}
- Round: {round_number}/5

**Attack Vector:**
{attack_vector.replace('_', ' ').title()}

**Impact:**
- Uptime: {sla.get('uptime', 0):.1f}%
- Latency: {sla.get('latency', 0):.0f}ms
- Error Rate: {sla.get('error_rate', 0):.1f}%

**Failures:**
{chr(10).join(f"- {f.get('service', '')}: {f.get('message', '')}" for f in failures[:3])}

Write the chaos story NOW (150 words max):
"""

        print(f"\n📝 GENERATING CHAOS STORY (Round {round_number})")
        print(f"   LLM: Grok")
        print(f"   Attack: {attack_vector}")
        print(f"   System Prompt: {system_prompt[:200]}...")
        print(f"   User Prompt: {user_prompt[:300]}...")

        response = await grok_client.chat(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.7,
            max_tokens=300
        )

        print(f"   ✅ LLM Response Length: {len(response)} chars")
        print(f"   Response Preview: {response[:150]}...")

        return response.strip()

    async def generate_architect_story(
        self,
        round_number: int,
        scenario: Dict[str, Any],
        chaos_result: Dict[str, Any],
        fix_result: Dict[str, Any]
    ) -> str:
        """
        Generate engineers' IMPERFECT fix response + new weakness

        Args:
            round_number: Current round (1-5)
            scenario: Full scenario data
            chaos_result: Chaos simulation results
            fix_result: Architect's fix deployment

        Returns:
            Story text (fix + new weakness reveal)
        """
        system_prompt = f"""You are narrating how engineers respond to the Chaos Monkey's attack.

This is Round {round_number} of 5.

Generate a FIX NARRATIVE (200-250 words):

Part 1 - PANIC & EMERGENCY RESPONSE (3-4 sentences):
- "After 30 minutes of panic, Raj has an idea..."
- Show time pressure, scrambling
- Senior engineer proposes fix
- Example: "After 45 minutes of chaos, the senior engineer Raj has an idea: 'Split the databases!' The team scrambles..."

Part 2 - THE FIX DEPLOYMENT (3-4 sentences):
- What they actually deployed
- Include technical details
- Timeline: "Within an hour...", "By 5:30 AM..."
- System recovers: uptime, latency, errors improve

Part 3 - RELIEF (1-2 sentences):
- CEO breathes, team celebrates
- "Crisis averted!" moment
- Example: "The CEO sighs with relief. 'Crisis averted!'"

Part 4 - CHAOS MONKEY'S OBSERVATION (2-3 sentences):
- **CRITICAL**: "But you, the Chaos Monkey 😈, notice something..."
- The fix INTRODUCED A NEW WEAKNESS
- Mischievous observation - you spot the new vulnerability
- Example: "But you grin wider, noticing their fix introduced a NEW vulnerability: services now call each other synchronously. One slow service will block the entire chain. Your work here isn't done..."

Tone: Story-driven. Show fix is IMPERFECT. Each solution creates new problems (realistic engineering).
"""

        sla_before = chaos_result.get("sla_metrics", {})
        sla_after = fix_result.get("post_fix_sla", {})

        user_prompt = f"""
**Chaos Attack:**
- Uptime dropped: 99% → {sla_before.get('uptime', 0):.1f}%
- Latency spiked: 200ms → {sla_before.get('latency', 0):.0f}ms
- Errors: {sla_before.get('error_rate', 0):.1f}%

**Your Fix:**
- Pattern: {fix_result.get('name', '')}
- Explanation: {fix_result.get('explanation', '')}
- Real World: {fix_result.get('real_world', '')}

**Post-Fix SLA:**
- Uptime: {sla_after.get('uptime', 0):.1f}%
- Latency: {sla_after.get('latency', 0):.0f}ms
- Errors: {sla_after.get('error_rate', 0):.1f}%

Write the System Architect's response NOW (150 words max):
"""

        print(f"\n📝 GENERATING ARCHITECT STORY (Round {round_number})")
        print(f"   LLM: Grok")
        print(f"   Fix: {fix_result.get('name', '')}")
        print(f"   System Prompt: {system_prompt[:200]}...")
        print(f"   User Prompt: {user_prompt[:300]}...")

        response = await grok_client.chat(
            prompt=user_prompt,
            system_prompt=system_prompt,
            temperature=0.6,
            max_tokens=300
        )

        print(f"   ✅ LLM Response Length: {len(response)} chars")
        print(f"   Response Preview: {response[:150]}...")

        return response.strip()


# Singleton instance
story_generator = StoryGenerator()
