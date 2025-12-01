"""
Chaos Monkey WebSocket Gameplay Handler
Real-time round-by-round chaos attacks and fixes
"""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect
from typing import Dict, Any
import json
import traceback
import time as time_module

from infrastructure.clients.redis_client import redis_client
from domain.services.round_orchestrator import round_orchestrator
from domain.services.tts_service import tts_service

router = APIRouter()


async def _generate_initial_system_diagram_llm(scenario: Dict[str, Any]) -> str:
    """
    Generate initial system diagram from scenario's architecture using LLM
    Shows the NAIVE architect's reasoning and the flaw
    """
    from infrastructure.clients.grok_client import grok_client

    initial_arch = scenario.get("initial_architecture", {})
    anti_pattern = scenario.get("anti_pattern", "")
    architect_reasoning = initial_arch.get("architect_reasoning", "Simple architecture")

    services = initial_arch.get("services", [])

    # Describe the initial system
    system_desc = []
    microservices = [s for s in services if s.get("type") == "microservice"]
    databases = [s for s in services if s.get("type") == "database"]

    if microservices:
        ms_names = ", ".join(s.get("name", "Service") for s in microservices[:3])
        system_desc.append(f"Microservices: {ms_names}")

    if databases:
        db_type = "SHARED single database" if len(databases) == 1 else "Multiple databases"
        system_desc.append(f"Database: {db_type}")

    system_desc.append(f"Architect's reasoning: \"{architect_reasoning}\"")
    system_desc.append(f"Anti-pattern: {anti_pattern.replace('_', ' ')}")

    weakness_desc = {
        "shared_database": "All services share ONE database - locks will cascade under load",
        "no_circuit_breaker": "No circuit breakers - one slow service = everyone waits",
        "no_rate_limiting": "No rate limiting - traffic surge = resource exhaustion",
        "sync_microservices": "Synchronous service calls - latency will propagate",
        "single_point_failure": "No redundancy - single crash = total outage"
    }

    system_prompt = """You are an expert at creating ASCII art diagrams of system architectures.

Generate a clean, readable ASCII diagram showing the NAIVE ARCHITECT'S initial system (before chaos reveals the flaw).

**Character context:** This is what a junior architect built with good intentions but naive decisions.

Rules:
1. Use box-drawing characters: ┌ ─ ┐ │ └ ┘ ├ ┤ ┬ ┴ ┼ ▼ ►
2. Show the architectural WEAKNESS (shared DB, no circuit breakers, etc.)
3. Keep it CONCISE (max 12 lines tall, max 60 chars wide)
4. Label each component clearly
5. Show flow top-to-bottom
6. Add the architect's reasoning as a comment at top
7. Add warning emoji showing the weakness

Example for shared database:
```
🏗️ "Why complicate things? One database is simpler."

┌────────────┐  ┌────────────┐  ┌────────────┐
│   Order    │  │  Payment   │  │ Inventory  │
└──────┬─────┘  └──────┬─────┘  └──────┬─────┘
       └────────────────┴────────────────┘
                        │
                   ┌────▼────┐
                   │Shared DB│ ⚠️ All writes = locks
                   └─────────┘
```

Output ONLY the ASCII diagram with architect quote."""

    user_prompt = f"""Initial System:
{chr(10).join(f"- {desc}" for desc in system_desc)}

Weakness: {weakness_desc.get(anti_pattern, 'System has architectural weakness')}

Generate an ASCII diagram showing this vulnerable architecture.
The diagram should make the weakness VISIBLE.

Output ONLY the ASCII diagram."""

    print(f"   🎨 Generating initial system diagram with LLM...")

    response = await grok_client.chat(
        prompt=user_prompt,
        system_prompt=system_prompt,
        temperature=0.3,
        max_tokens=400
    )

    return f"""CURRENT SYSTEM (Running Smoothly... For Now)
────────────────────────────────────────────

{response.strip()}

⚠️ WEAKNESS: {weakness_desc.get(anti_pattern, 'Architectural vulnerability present')}
"""


@router.websocket("/ws/chaos/{session_id}")
async def chaos_gameplay(websocket: WebSocket, session_id: str):
    """
    WebSocket handler for chaos gameplay loop

    Flow per round:
    1. Send round intro
    2. Receive user's attack selection (MC or manual)
    3. Simulate chaos attack
    4. Generate chaos story
    5. System Architect deploys fix
    6. Generate architect story
    7. Generate ASCII animation
    8. Send results to client
    9. Repeat until 5 rounds complete
    """
    await websocket.accept()
    print(f"\n🔌 WebSocket connected: {session_id}")

    try:
        # Load session from Redis
        print(f"   📥 Loading session from Redis...")
        session_data = await redis_client.get_session(session_id)
        if not session_data:
            print(f"   ❌ Session not found!")
            await websocket.send_json({
                "type": "error",
                "message": "Session not found or expired"
            })
            await websocket.close()
            return

        print(f"   ✅ Session loaded: Round {session_data['current_round']}/{session_data['max_rounds']}")

        scenario = session_data["scenario"]
        current_round = session_data["current_round"]
        max_rounds = session_data["max_rounds"]

        # Parse JSONB fields if they're strings (same fix as admin_routes.py)
        system_state = session_data.get("system_state", {})
        if isinstance(system_state, str):
            system_state = json.loads(system_state)
            session_data["system_state"] = system_state

        # Also parse initial_architecture in scenario if needed
        if "initial_architecture" in scenario and isinstance(scenario["initial_architecture"], str):
            scenario["initial_architecture"] = json.loads(scenario["initial_architecture"])

        # Send initial scenario intro (only Round 1)
        if current_round == 1:
            # Generate initial system visualization with LLM
            initial_system_diagram = await _generate_initial_system_diagram_llm(scenario)

            # Generate TTS for scenario intro (🎬 Scenario)
            intro_text = f"{scenario['story_title']}. {scenario['story_intro']}. Stakes: {scenario['story_stakes']}"
            tts_start = time_module.time()
            intro_audio_url = await tts_service.generate_story_audio(
                text=intro_text,
                story_type="chaos",  # Use chaos voice for dramatic intro
                session_id=session_id,
                round_number=0  # Round 0 = intro
            )
            tts_elapsed = time_module.time() - tts_start
            print(f"   🔊 Intro TTS generated in {tts_elapsed:.2f}s")

            await websocket.send_json({
                "type": "scenario_intro",
                "title": scenario["story_title"],
                "intro": scenario["story_intro"],
                "stakes": scenario["story_stakes"],
                "theme": scenario["story_theme"],
                "difficulty": scenario["difficulty"],
                "initial_system": initial_system_diagram,  # Show system running
                "audio_url": intro_audio_url
            })

        # Main game loop
        while current_round <= max_rounds:
            deployed_fixes = session_data.get("deployed_fixes", [])
            attack_history = session_data.get("attack_history", {})

            # Check if we have pre-generated attacks from previous round
            if current_round == 1:
                # Round 1: Generate initial attack options
                print(f"\n🎯 ROUND {current_round} - Generating initial attack options")
                attack_phase = await round_orchestrator.orchestrate_round(
                    round_number=current_round,
                    scenario=scenario,
                    system_state=session_data["system_state"],
                    deployed_fixes=deployed_fixes,
                    attack_history=attack_history,
                    selected_attack=None  # Generate initial options
                )

                discovered_anti_patterns = attack_phase.get("discovered_anti_patterns", [])
                chaos_monkey_analysis = attack_phase.get("chaos_monkey_analysis", "")
                attack_options = attack_phase.get("attack_options", [])
            else:
                # Round 2+: Use pre-generated attacks from previous round
                print(f"\n🎯 ROUND {current_round} - Using pre-generated attacks")
                attack_options = session_data.get("next_round_attacks", [])
                discovered_anti_patterns = []  # Already discovered in previous round
                chaos_monkey_analysis = f"Round {current_round} attacks ready"
                print(f"   ✅ Loaded {len(attack_options)} pre-generated attacks")

            # Send round start with LLM analysis and attacks
            await websocket.send_json({
                "type": "round_start",
                "round": current_round,
                "max_rounds": max_rounds,
                "anti_pattern": scenario["anti_pattern"],
                "attack_history": session_data.get("attack_history", {}),
                "discovered_anti_patterns": discovered_anti_patterns,  # What LLM found
                "chaos_monkey_analysis": chaos_monkey_analysis,  # LLM's observation
                "attack_options": attack_options  # Dynamically generated attacks
            })

            # Wait for user's attack selection
            message = await websocket.receive_text()
            attack_data = json.loads(message)

            if attack_data.get("type") != "attack_submit":
                await websocket.send_json({
                    "type": "error",
                    "message": "Expected attack_submit message"
                })
                continue

            attack_vector = attack_data.get("attack_vector", "")
            attack_params = attack_data.get("attack_params", "")

            # Validate attack
            if not attack_vector:
                await websocket.send_json({
                    "type": "error",
                    "message": "Please select an attack vector"
                })
                continue

            # Execute selected attack (LLM call that also generates next round's attacks)
            print(f"\n🎯 ROUND {current_round} - Executing '{attack_vector}' + preparing Round {current_round + 1}")

            # Track attack history
            if "attack_history" not in session_data:
                session_data["attack_history"] = {}
            attack_history = session_data["attack_history"]
            attack_count = attack_history.get(attack_vector, 0) + 1
            attack_history[attack_vector] = attack_count

            # Execute selected attack
            round_result = await round_orchestrator.orchestrate_round(
                round_number=current_round,
                scenario=scenario,
                system_state=session_data["system_state"],
                deployed_fixes=session_data.get("deployed_fixes", []),
                attack_history=attack_history,
                selected_attack=attack_vector,  # Phase 2: Execute
                attack_params=attack_params
            )

            # Extract results from LLM response
            chaos_story = round_result.get("chaos_story", "")
            chaos_result = {
                "sla_metrics": round_result.get("sla_after_attack", {}),
                "failures": round_result.get("failures", []),
                "was_mitigated": False
            }

            print(f"   ❌ SLA Impact: Uptime {chaos_result['sla_metrics'].get('uptime', 0):.1f}%, Latency {chaos_result['sla_metrics'].get('latency', 0):.0f}ms")

            # Generate TTS for chaos story (💥 THE INCIDENT)
            tts_start = time_module.time()
            chaos_audio_url = await tts_service.generate_story_audio(
                text=chaos_story,
                story_type="chaos",
                session_id=session_id,
                round_number=current_round
            )
            tts_elapsed = time_module.time() - tts_start
            print(f"   🔊 Chaos TTS generated in {tts_elapsed:.2f}s")

            # Send chaos story
            await websocket.send_json({
                "type": "chaos_story",
                "story": chaos_story,
                "sla_metrics": chaos_result["sla_metrics"],
                "failures": chaos_result.get("failures", []),
                "was_mitigated": False,
                "audio_url": chaos_audio_url
            })

            # Extract fix and architect story from LLM response
            architect_story = round_result.get("architect_story", "")
            fix_result = {
                "fix": round_result.get("fix_deployed", "generic_fix"),
                "name": round_result.get("fix_name", "Generic Fix"),
                "explanation": round_result.get("fix_explanation", ""),
                "why": round_result.get("fix_why", ""),
                "real_world": round_result.get("fix_real_world", ""),
                "post_fix_sla": round_result.get("sla_after_fix", {}),
                "new_system_state": round_result.get("new_system_state", session_data["system_state"])
            }

            print(f"   ✅ Fix deployed: {fix_result['name']}")

            # Get ASCII diagram from round result
            animation = round_result.get("ascii_diagram", "System diagram not generated")

            # Update system state with LLM-generated new state
            new_system_state = fix_result.get("new_system_state", session_data["system_state"])
            session_data["system_state"] = new_system_state

            # Track deployed fix
            if "deployed_fixes" not in session_data:
                session_data["deployed_fixes"] = []
            deployed_fix_name = fix_result.get("fix")
            if deployed_fix_name and deployed_fix_name not in session_data["deployed_fixes"]:
                session_data["deployed_fixes"].append(deployed_fix_name)

            # Store next round's attack options (generated in same LLM call!)
            next_round_attacks = round_result.get("next_round_attacks", [])
            if next_round_attacks:
                session_data["next_round_attacks"] = next_round_attacks
                print(f"   ✅ Next round has {len(next_round_attacks)} attacks ready")

            print(f"   💾 System state updated with fix: {deployed_fix_name}")
            print(f"   📊 Total deployed fixes: {session_data['deployed_fixes']}")

            # Save round to session
            round_data = {
                "round_number": current_round,
                "attack_vector": attack_vector,
                "attack_params": attack_params,
                "chaos_story": chaos_story,
                "chaos_result": chaos_result,
                "architect_story": architect_story,
                "fix_result": fix_result,
                "ascii_animation": animation
            }
            session_data["rounds"].append(round_data)

            # Generate TTS for architect story (⚡ EMERGENCY RESPONSE)
            tts_start = time_module.time()
            architect_audio_url = await tts_service.generate_story_audio(
                text=architect_story,
                story_type="architect",
                session_id=session_id,
                round_number=current_round
            )
            tts_elapsed = time_module.time() - tts_start
            print(f"   🔊 Architect TTS generated in {tts_elapsed:.2f}s")

            # Send round complete
            await websocket.send_json({
                "type": "round_complete",
                "round": current_round,
                "chaos_story": chaos_story,
                "architect_story": architect_story,
                "ascii_animation": animation,
                "fix_name": fix_result["name"],
                "fix_explanation": fix_result["explanation"],
                "fix_why": fix_result["why"],
                "fix_real_world": fix_result["real_world"],
                "sla_before": chaos_result["sla_metrics"],
                "sla_after": fix_result["post_fix_sla"],
                "audio_url": architect_audio_url
            })

            # Increment round
            current_round += 1
            session_data["current_round"] = current_round

            # Update Redis session
            await redis_client.set_session(session_id, session_data, ttl=86400)
            print(f"   ✅ Round {current_round - 1} complete, session saved")

        # Game complete
        print(f"\n🎉 GAME COMPLETE - All {max_rounds} rounds finished!")
        session_data["is_completed"] = True
        await redis_client.set_session(session_id, session_data, ttl=86400)

        await websocket.send_json({
            "type": "game_complete",
            "message": "Congratulations! You've completed all 5 rounds of chaos testing.",
            "rounds": session_data["rounds"]
        })

        print(f"   Session {session_id} marked as completed")

        # TODO: Persist completed session to PostgreSQL

    except WebSocketDisconnect:
        print(f"Client disconnected: {session_id}")
    except Exception as e:
        print(f"Error in chaos gameplay: {e}")
        traceback.print_exc()
        try:
            await websocket.send_json({
                "type": "error",
                "message": f"Server error: {str(e)}"
            })
        except:
            pass
    finally:
        try:
            await websocket.close()
        except:
            pass
