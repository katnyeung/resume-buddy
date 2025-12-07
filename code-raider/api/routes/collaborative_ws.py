"""
WebSocket endpoint for real-time collaborative gameplay
This is the CORE of the game - handles full round-robin flow
"""
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy import select
from datetime import datetime
import json

from infrastructure.database import get_db
from infrastructure.clients.redis_client import redis_client
from domain.models import GameSession, SessionRound
from domain.services.dynamic_story_generator import story_generator
from domain.services.round_robin_engine import round_robin_engine
from domain.services.tts_service import tts_service

router = APIRouter(tags=["websocket"])


@router.websocket("/collab/{session_id}")
async def gameplay_websocket(websocket: WebSocket, session_id: str):
    """
    WebSocket endpoint for collaborative coding game

    Flow:
    1. Client connects
    2. Load session from Redis
    3. Send initial story + skeleton code (Round 1)
    4. Wait for user input
    5. Execute code → Generate response (story + animation + audio)
    6. Repeat for max 5 rounds or until all tests pass
    7. Persist to PostgreSQL when complete
    """
    print(f"🔌 WebSocket connection attempt for session: {session_id}")
    await websocket.accept()
    print(f"✅ WebSocket accepted for session: {session_id}")

    try:
        # Load session from Redis
        print(f"📦 Loading session from Redis: {session_id}")
        session = await redis_client.get_session(session_id)
        if not session:
            print(f"❌ Session not found in Redis: {session_id}")
            await websocket.send_json({"type": "error", "message": "Session not found"})
            await websocket.close()
            return

        print(f"✅ Session loaded: {session.get('problem_id')}")
        print(f"📊 Session data: current_round={session.get('current_round')}, max_rounds={session.get('max_rounds')}")

        problem = session["problem"]
        current_round = session.get("current_round", 1)
        max_rounds = session.get("max_rounds", 5)

        # Send initial content when client connects (regardless of round)
        print(f"🎯 Client connected at round {current_round}/{max_rounds}")

        # If round 1, send initial story + skeleton code
        if current_round == 1:
            print(f"🎬 Generating Round 1 content...")
            round_data = await story_generator.generate_round_response(
                round_number=1,
                problem=problem
            )
            print(f"✅ Round 1 content generated")

            # Generate TTS audio for story and CIPHER
            print(f"🔊 Generating TTS audio...")
            story_audio_url = await tts_service.generate_audio(
                text=round_data["story"],
                emotion=round_data["emotion"],
                session_id=session_id,
                round_number=1,
                voice_type="narrator"  # Story narrator voice
            )
            cipher_audio_url = await tts_service.generate_audio(
                text=round_data["ai_message"],
                emotion=round_data["emotion"],
                session_id=f"{session_id}_cipher",
                round_number=1,
                voice_type="cipher"  # CIPHER's AI mentor voice
            )
            print(f"✅ TTS audio generated: story={story_audio_url}, cipher={cipher_audio_url}")

            # Send to client
            print(f"📤 Sending round_start message to client...")
            await websocket.send_json({
                "type": "round_start",
                "round": 1,
                "story": round_data["story"],
                "ai_message": round_data["ai_message"],
                "code": round_data.get("skeleton_code", ""),
                "animation": round_data.get("ascii_scene", ""),
                "story_audio_url": story_audio_url,
                "cipher_audio_url": cipher_audio_url,
                "emotion": round_data["emotion"]
            })
            print(f"✅ Message sent to client")

            # Store AI code for later combination
            session["ai_code"] = round_data.get("skeleton_code", "")
            await redis_client.update_session(session_id, {"ai_code": session["ai_code"]})

            # Store round data
            await redis_client.add_round(session_id, {
                "round_number": 1,
                "ai_code": session["ai_code"],
                "story_text": round_data["story"],
                "ai_message": round_data["ai_message"],
                "ascii_animation": round_data.get("ascii_scene", ""),
                "emotion": round_data["emotion"],
                "story_audio_url": story_audio_url,
                "cipher_audio_url": cipher_audio_url
            })

        else:
            # Client reconnected to existing session (round > 1)
            print(f"⚠️ Session already at round {current_round}. Resetting to Round 1...")

            # Reset session to Round 1
            await redis_client.update_session(session_id, {
                "current_round": 1,
                "rounds": []
            })
            current_round = 1

            # Generate Round 1 content
            print(f"🎬 Generating Round 1 content (after reset)...")
            round_data = await story_generator.generate_round_response(
                round_number=1,
                problem=problem
            )
            print(f"✅ Round 1 content generated")

            # Generate TTS audio for story and CIPHER
            print(f"🔊 Generating TTS audio...")
            reset_story_audio = await tts_service.generate_audio(
                text=round_data["story"],
                emotion=round_data["emotion"],
                session_id=session_id,
                round_number=1,
                voice_type="narrator"
            )
            reset_cipher_audio = await tts_service.generate_audio(
                text=round_data["ai_message"],
                emotion=round_data["emotion"],
                session_id=f"{session_id}_cipher",
                round_number=1,
                voice_type="cipher"
            )
            print(f"✅ TTS audio generated: story={reset_story_audio}, cipher={reset_cipher_audio}")

            # Send to client
            print(f"📤 Sending round_start message (after reset)...")
            await websocket.send_json({
                "type": "round_start",
                "round": 1,
                "story": round_data["story"],
                "ai_message": round_data["ai_message"],
                "code": round_data.get("skeleton_code", ""),
                "animation": round_data.get("ascii_scene", ""),
                "story_audio_url": reset_story_audio,
                "cipher_audio_url": reset_cipher_audio,
                "emotion": round_data["emotion"]
            })
            print(f"✅ Message sent to client")

            # Store AI code
            session["ai_code"] = round_data.get("skeleton_code", "")
            await redis_client.update_session(session_id, {"ai_code": session["ai_code"]})

            # Store round data (reset scenario)
            await redis_client.add_round(session_id, {
                "round_number": 1,
                "ai_code": session["ai_code"],
                "story_text": round_data["story"],
                "ai_message": round_data["ai_message"],
                "ascii_animation": round_data.get("ascii_scene", ""),
                "emotion": round_data["emotion"],
                "story_audio_url": reset_story_audio,
                "cipher_audio_url": reset_cipher_audio
            })

        # Main game loop: Wait for user input
        while current_round <= max_rounds:
            # Receive user input
            message = await websocket.receive_json()

            if message.get("type") == "user_reasoning":
                user_reasoning = message.get("reasoning", "")
                conversation_history = message.get("conversation_history", [])

                print(f"💭 User reasoning: {user_reasoning[:100]}...")
                print(f"📚 Conversation history: {len(conversation_history)} messages")
                if conversation_history:
                    print(f"📜 Last 3 messages: {conversation_history[-3:]}")

                # Step 1: Generate code + evaluate against ALL test cases (1 LLM call)
                print(f"🤖 Generating code and evaluating against all test cases...")
                from infrastructure.clients.grok_client import grok_client

                code_and_analysis = await grok_client.generate_code_and_evaluate(
                    user_reasoning=user_reasoning,
                    problem=problem,
                    round_number=current_round,
                    conversation_history=conversation_history  # Pass history for iterative code building
                )
                user_generated_code = code_and_analysis['code']
                edge_case_analysis = code_and_analysis['edge_case_analysis']
                intent_level = code_and_analysis.get('intent_level', 'unknown')
                print(f"✅ Generated {len(user_generated_code)} chars from user's approach")
                print(f"   Intent level: {intent_level}")
                print(f"   Edge case analysis: {edge_case_analysis[:100]}...")

                # If QUESTION intent, skip execution and story generation
                if intent_level == 'question':
                    print(f"❓ User asked a question - skipping execution and story")

                    # Send response with NO story, NO execution, just CIPHER's explanation
                    await websocket.send_json({
                        "type": "round_result",
                        "round": current_round,
                        "story": "",  # No story progression
                        "ai_message": edge_case_analysis,  # CIPHER's explanation
                        "code": user_generated_code,  # Same code as before
                        "animation": "",  # No new animation
                        "execution_result": {},
                        "story_audio_url": None,  # No TTS
                        "cipher_audio_url": None,  # No TTS (optional: could add TTS for explanation)
                        "emotion": "thoughtful"
                    })

                    # Don't increment round, don't add to history, just continue loop
                    continue

                # Step 2: Execute on STORY VAULTS ONLY (first test case)
                story_test_case = problem['test_cases'][0] if problem.get('test_cases') else {}
                current_test_case = [story_test_case]
                print(f"⚙️ Executing on story vaults: {story_test_case.get('input', {})}")

                execution_result = await round_robin_engine.execute_code(
                    user_generated_code,
                    current_test_case
                )
                print(f"✅ Execution complete: {execution_result.get('passed')}/{execution_result.get('total')} tests passed")

                # Step 3: Story generation with edge case analysis (1 LLM call)
                print(f"🎬 Generating story response (1 LLM call)...")
                round_data = await story_generator.generate_round_response(
                    round_number=current_round,
                    problem=problem,
                    user_input=user_reasoning,
                    execution_result=execution_result,
                    combined_code=user_generated_code,
                    conversation_history=conversation_history,
                    current_test_case=current_test_case[0] if current_test_case else None,
                    edge_case_analysis=edge_case_analysis  # Pass Grok's analysis for teaching
                )
                print(f"✅ Story generated")

                # Generate TTS audio for story
                story_audio_url = await tts_service.generate_audio(
                    text=round_data["story"],
                    emotion=round_data["emotion"],
                    session_id=session_id,
                    round_number=current_round,
                    voice_type="narrator"  # Story narrator voice
                )

                # Generate TTS audio for CIPHER's message
                cipher_audio_url = await tts_service.generate_audio(
                    text=round_data["ai_message"],
                    emotion=round_data["emotion"],
                    session_id=f"{session_id}_cipher",
                    round_number=current_round,
                    voice_type="cipher"  # CIPHER's AI mentor voice
                )

                # Send response to client
                await websocket.send_json({
                    "type": "round_result",
                    "round": current_round,
                    "story": round_data["story"],
                    "ai_message": round_data["ai_message"],
                    "code": user_generated_code,  # Show user's generated code
                    "animation": round_data.get("ascii_animation", ""),
                    "execution_result": execution_result,
                    "story_audio_url": story_audio_url,  # Story TTS
                    "cipher_audio_url": cipher_audio_url,  # CIPHER TTS
                    "emotion": round_data["emotion"]
                })

                # Store round data in Redis
                await redis_client.add_round(session_id, {
                    "round_number": current_round,
                    "user_reasoning": user_reasoning,
                    "user_generated_code": user_generated_code,  # Store user's code
                    "execution_result": execution_result,
                    "story_text": round_data["story"],
                    "ai_message": round_data["ai_message"],
                    "ascii_animation": round_data.get("ascii_animation", ""),
                    "emotion": round_data["emotion"],
                    "story_audio_url": story_audio_url,
                "cipher_audio_url": cipher_audio_url
                })

                # Check if user has mastered the concept (Grok decides!)
                # Grok returns "user_mastered": true/false in round_data
                user_mastered_concept = round_data.get("user_mastered", False)

                print(f"🎯 Completion check (round {current_round}):")
                print(f"   Grok says user_mastered: {user_mastered_concept}")

                # End if Grok says mastered OR max rounds reached
                should_continue = not user_mastered_concept and current_round < max_rounds

                if user_mastered_concept:
                    print(f"🎉 Grok confirmed: User mastered the algorithm! Ending game.")
                elif current_round >= max_rounds:
                    print(f"⏱️ Max rounds reached. Ending game.")
                    should_continue = False

                if not should_continue:
                    # Game complete! Calculate final score
                    final_score = round_robin_engine.calculate_final_score(execution_result)

                    # Generate learning summary
                    session_reload = await redis_client.get_session(session_id)
                    learning = await story_generator.generate_learning_summary(
                        problem=problem,
                        all_rounds=session_reload.get("rounds", []),
                        final_score=final_score,
                        mastered_early=user_mastered_concept  # Celebrate if they mastered it!
                    )

                    # Generate TTS for summary
                    summary_audio = await tts_service.generate_audio(
                        text=learning["summary"],
                        emotion=learning["emotion"],
                        session_id=session_id,
                        round_number=99  # Special round for summary
                    )

                    # Send learning summary
                    await websocket.send_json({
                        "type": "game_complete",
                        "final_score": final_score,
                        "summary": learning["summary"],
                        "audio_url": summary_audio,
                        "emotion": learning["emotion"]
                    })

                    # Persist to PostgreSQL
                    await persist_session_to_db(session_id, session_reload, final_score)

                    # Mark session as complete
                    await redis_client.update_session(session_id, {"is_completed": True})

                    break

                # Update AI code for next round (use suggested code if provided)
                if round_data.get("suggested_code"):
                    session["ai_code"] = round_data["suggested_code"]
                    await redis_client.update_session(session_id, {"ai_code": session["ai_code"]})

                # Increment round
                current_round += 1
                await redis_client.update_session(session_id, {"current_round": current_round})

    except WebSocketDisconnect:
        print(f"WebSocket disconnected for session {session_id}")
    except Exception as e:
        import traceback
        import httpx

        print(f"WebSocket error: {e}")
        print(f"Traceback:\n{traceback.format_exc()}")

        # Send user-friendly error message
        error_msg = str(e)
        if isinstance(e, httpx.ReadTimeout):
            error_msg = "⏱️ Grok API took too long to respond (timeout after 120s). This can happen with complex prompts or high API load. Please try again!"

        try:
            await websocket.send_json({"type": "error", "message": error_msg})
        except:
            pass  # Connection already closed
    finally:
        try:
            await websocket.close()
        except:
            pass  # Connection already closed


async def persist_session_to_db(session_id: str, session_data: dict, final_score: int):
    """
    Persist completed session from Redis to PostgreSQL
    """
    from infrastructure.database import AsyncSessionLocal

    async with AsyncSessionLocal() as db:
        try:
            # Create GameSession record
            game_session = GameSession(
                id=session_id,
                user_id=session_data["user_id"],
                problem_id=session_data["problem_id"],
                current_round=session_data.get("current_round", 1),
                max_rounds=session_data.get("max_rounds", 5),
                is_completed=True,
                final_score=final_score,
                completed_at=datetime.utcnow()
            )
            db.add(game_session)

            # Create SessionRound records
            for round_data in session_data.get("rounds", []):
                session_round = SessionRound(
                    session_id=session_id,
                    round_number=round_data.get("round_number"),
                    user_input_text=round_data.get("user_input_text"),
                    user_code=round_data.get("user_code"),
                    ai_code=round_data.get("ai_code"),
                    combined_code=round_data.get("combined_code"),
                    execution_result=round_data.get("execution_result"),
                    story_text=round_data.get("story_text"),
                    ai_message=round_data.get("ai_message"),
                    ascii_animation=round_data.get("ascii_animation"),
                    emotion=round_data.get("emotion"),
                    tts_audio_url=round_data.get("tts_audio_url")
                )
                db.add(session_round)

            await db.commit()

            # Delete from Redis after successful persist
            await redis_client.delete_session(session_id)

        except Exception as e:
            await db.rollback()
            print(f"Error persisting session to DB: {e}")
