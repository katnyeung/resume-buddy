"""WebSocket endpoints for real-time streaming interview."""
import base64
import time
import tempfile
import os
import asyncio
from pathlib import Path
from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Depends, HTTPException
from sqlalchemy.orm import Session

from domain.models import InterviewSession, SessionRound
from infrastructure.database import get_db
from infrastructure.websocket.audio_buffer import AudioBuffer
from infrastructure.websocket.connection_manager import manager
from infrastructure.clients.openai_client import text_to_speech, transcribe_audio
from infrastructure.clients.redis_client import get_redis_store
from domain.agents.interrupt_agent import InterruptAgent
from domain.services.question_evaluation import generate_question, evaluate_answer
from domain.services.persistence import persist_round_to_database


router = APIRouter(prefix="/ws/interview", tags=["websocket"])


async def collect_user_answer(
    websocket: WebSocket,
    session_id: str,
    round_number: int,
    question_text: str,
    session: InterviewSession,
    audio_buffer: AudioBuffer,
    interrupt_agent: InterruptAgent,
    last_interrupt_time: float
) -> tuple[str, float]:
    """
    Collect user's voice answer with real-time transcription and AI interrupts.

    Returns:
        tuple: (full_transcript, updated_last_interrupt_time)
    """
    full_transcript = ""
    start_time = time.time()
    chunks_received = 0
    failed_transcriptions = 0
    MAX_FAILED_TRANSCRIPTIONS = 3
    MAX_RECORDING_TIME = 180  # 3 minutes max

    try:
        while True:
            # Check if max time exceeded
            elapsed = time.time() - start_time
            if elapsed > MAX_RECORDING_TIME:
                print(f"[WebSocket] Max recording time reached, ending answer")
                break

            # Receive audio chunk from client (with timeout)
            try:
                data = await asyncio.wait_for(websocket.receive(), timeout=1.0)
            except asyncio.TimeoutError:
                # Timeout - no data arrived in 1 second, just continue waiting
                # Frontend controls all transcription timing
                continue
            except RuntimeError as e:
                if "disconnect" in str(e).lower() or "websocket" in str(e).lower():
                    print(f"[WebSocket] Client disconnected during answer collection")
                    raise WebSocketDisconnect()
                raise

            if "bytes" in data:
                audio_chunk = data["bytes"]
                audio_buffer.append(audio_chunk)
                chunks_received += 1

                # Log every 100 chunks to verify audio is being received
                if chunks_received % 100 == 0:
                    buffer_duration = audio_buffer.duration_seconds()
                    print(f"[WebSocket] Received {chunks_received} chunks, buffer: {buffer_duration:.1f}s")
                # Frontend now controls all transcription timing via transcribe_now messages

            elif "text" in data:
                # Client sent control message (JSON string)
                import json
                try:
                    message_json = json.loads(data["text"])
                    msg = message_json.get("text", "")
                    print(f"[WebSocket] Received control message: {msg}")
                except json.JSONDecodeError:
                    # Fallback: treat as plain string
                    msg = data["text"]
                    print(f"[WebSocket] Received text (non-JSON): {msg}")

                if msg == "end_answer":
                    print(f"[WebSocket] Client requested end")
                    break
                elif msg == "transcribe_final":
                    # Frontend detected 10s silence - this is the FINAL transcription before evaluation
                    print(f"[WebSocket] Client requested FINAL transcription (10s silence detected)")
                    buffer_duration = audio_buffer.duration_seconds()
                    if buffer_duration >= 2.0:  # Only transcribe if we have at least 2 seconds
                        print(f"[WebSocket] Processing final transcription ({buffer_duration:.1f}s buffered)")

                        # Save accumulated buffer to temp file
                        temp_audio_path = os.path.join(
                            tempfile.gettempdir(),
                            f"final_{session_id}_{round_number}_{time.time()}.webm"
                        )
                        accumulated_data = audio_buffer.get_accumulated_with_header()
                        with open(temp_audio_path, "wb") as f:
                            f.write(accumulated_data)

                        audio_buffer.clear()

                        # Transcribe chunk
                        try:
                            chunk_transcript = await transcribe_audio(temp_audio_path)

                            if chunk_transcript and chunk_transcript.strip():
                                full_transcript += " " + chunk_transcript

                                # Send live transcript to client
                                await websocket.send_json({
                                    "type": "transcript",
                                    "text": chunk_transcript
                                })

                                print(f"[WebSocket] Final transcription complete, breaking collection loop")
                            else:
                                print(f"[WebSocket] Final transcription empty, breaking collection loop anyway")

                        except Exception as e:
                            print(f"Final transcription error: {e}")
                        finally:
                            # Clean up temp file
                            if os.path.exists(temp_audio_path):
                                os.remove(temp_audio_path)

                    # BREAK the collection loop - move to evaluation
                    print(f"[WebSocket] Ending answer collection (10s silence)")
                    break

                elif msg == "transcribe_now":
                    # Frontend requests immediate transcription (e.g., detected a pause)
                    buffer_duration = audio_buffer.duration_seconds()
                    if buffer_duration >= 2.0:  # Only transcribe if we have at least 2 seconds
                        print(f"[WebSocket] Client requested transcription ({buffer_duration:.1f}s buffered)")

                        # Save accumulated buffer to temp file
                        temp_audio_path = os.path.join(
                            tempfile.gettempdir(),
                            f"requested_{session_id}_{round_number}_{time.time()}.webm"
                        )
                        accumulated_data = audio_buffer.get_accumulated_with_header()
                        with open(temp_audio_path, "wb") as f:
                            f.write(accumulated_data)

                        audio_buffer.clear()

                        # Transcribe chunk
                        try:
                            chunk_transcript = await transcribe_audio(temp_audio_path)

                            if chunk_transcript and chunk_transcript.strip():
                                full_transcript += " " + chunk_transcript
                                failed_transcriptions = 0

                                # Store WebM header after first success
                                if audio_buffer.stored_header is None:
                                    print(f"[WebSocket] First successful transcription - extracting WebM header")
                                    audio_buffer.store_header_from_file(temp_audio_path)

                                # Clear accumulated chunks to prevent duplicates
                                audio_buffer.accumulated_chunks = []
                                print(f"[WebSocket] Cleared accumulated chunks (keeping header for next cycle)")

                                # Send live transcript to client
                                await websocket.send_json({
                                    "type": "transcript",
                                    "text": chunk_transcript
                                })

                                # Check for interrupt (frontend-triggered, on every transcription)
                                word_count = len(full_transcript.split())

                                # Skip interrupts if interrupt_level is NONE or not set
                                interrupt_enabled = session.interrupt_level and session.interrupt_level != "NONE"

                                if interrupt_enabled and word_count > 20:  # At least 20 words before considering interrupt
                                    current_time = time.time()
                                    duration_seconds = current_time - start_time
                                    time_since_last_interrupt = current_time - last_interrupt_time

                                    # Only interrupt if enough time has passed (45s cooldown)
                                    if time_since_last_interrupt >= 45.0:
                                        print(f"[WebSocket] Checking for interrupt (transcript: {word_count} words, duration: {duration_seconds:.0f}s)")

                                        await websocket.send_json({
                                            "type": "ai_thinking",
                                            "message": "AI is analyzing your answer..."
                                        })

                                        decision = await interrupt_agent.analyze(
                                            transcript=full_transcript,
                                            question=question_text,
                                            interview_type=session.interview_type,
                                            difficulty_level=session.difficulty_level,
                                            interrupt_level=session.interrupt_level,
                                            duration_seconds=duration_seconds
                                        )

                                        if decision["should_interrupt"]:
                                            print(f"[WebSocket] AI decided to interrupt: {decision['reason']}")

                                            audio_buffer.clear()
                                            audio_buffer.reset_accumulator()
                                            print(f"[WebSocket] Cleared buffer and reset accumulator")

                                            # Generate interrupt TTS
                                            interrupt_audio_path = os.path.join(
                                                tempfile.gettempdir(),
                                                f"interrupt_{session_id}_{round_number}_{time.time()}.mp3"
                                            )
                                            await text_to_speech(decision["feedback_text"], interrupt_audio_path)

                                            with open(interrupt_audio_path, "rb") as f:
                                                interrupt_audio_base64 = base64.b64encode(f.read()).decode()

                                            await websocket.send_json({
                                                "type": "interrupt",
                                                "text": decision["feedback_text"],
                                                "reason": decision["reason"],
                                                "interrupt_type": decision["interrupt_type"],
                                                "audio_base64": interrupt_audio_base64,
                                                "restart_recording": True
                                            })

                                            last_interrupt_time = current_time
                                            start_time = time.time()
                                            print(f"[WebSocket] Reset recording timer after interrupt")
                                        else:
                                            last_interrupt_time = current_time
                                            print(f"[WebSocket] AI decided not to interrupt")

                        except Exception as e:
                            print(f"Transcription error: {e}")
                        finally:
                            # Clean up temp file
                            if os.path.exists(temp_audio_path):
                                os.remove(temp_audio_path)
                    else:
                        print(f"[WebSocket] Ignoring transcribe_now request (only {buffer_duration:.1f}s buffered, need ≥2s)")

    except WebSocketDisconnect:
        print(f"Client disconnected from session {session_id}")
        raise

    return full_transcript.strip(), last_interrupt_time


@router.websocket("/{session_id}/rounds/{round_number}/stream")
async def stream_interview_round(
    websocket: WebSocket,
    session_id: str,
    round_number: int
):
    """
    Real-time streaming interview with AI interruptions.

    Test connection: ws://localhost:8086/ws/interview/{session_id}/rounds/{round_number}/stream

    Flow:
    1. Client connects
    2. Server sends question (audio + text)
    3. Client streams audio chunks
    4. Server transcribes in real-time
    5. Server sends live transcript to client
    6. Server decides when to interrupt (based on interrupt_level)
    7. Server sends interrupt feedback (audio + text)
    8. Continues until user stops speaking (VAD)
    9. Server sends final evaluation
    10. Connection closes
    """
    print(f"[WebSocket] Connection attempt: session={session_id}, round={round_number}")

    try:
        await manager.connect(session_id, websocket)
        print(f"[WebSocket] Connected successfully")
    except Exception as e:
        print(f"[WebSocket] Connection failed: {e}")
        raise

    # Get session from database
    db = next(get_db())
    try:
        print(f"[WebSocket] Querying session: {session_id}")
        session = db.query(InterviewSession).filter(
            InterviewSession.id == session_id
        ).first()

        if not session:
            print(f"[WebSocket] Session not found: {session_id}")
            await websocket.send_json({"type": "error", "message": "Session not found"})
            await websocket.close()
            return

        print(f"[WebSocket] Session found: {session.id}")

        print(f"[WebSocket] Querying round: {round_number}")
        round_record = db.query(SessionRound).filter(
            SessionRound.session_id == session_id,
            SessionRound.round_number == round_number
        ).first()

        if not round_record:
            print(f"[WebSocket] Round not found: {round_number}")
            await websocket.send_json({"type": "error", "message": f"Round {round_number} not found"})
            await websocket.close()
            return

        print(f"[WebSocket] Round found, status: {round_record.status}")

        # Validate round can be started
        if round_record.status == "COMPLETED":
            print(f"[WebSocket] Round already completed")
            await websocket.send_json({"type": "error", "message": "Round already completed"})
            await websocket.close()
            return

        # Mark round as IN_PROGRESS if PENDING
        if round_record.status == "PENDING":
            print(f"[WebSocket] Marking round as IN_PROGRESS")
            round_record.status = "IN_PROGRESS"
            round_record.started_at = db.func.now()
            db.commit()
            print(f"[WebSocket] Round status updated")

        # Close database connection early (WebSocket session can last minutes, avoid timeout)
        # We'll create a new session when we need to save results at the end
        try:
            db.close()
            print(f"[WebSocket] Database connection closed after initial setup")
        except Exception as e:
            print(f"[WebSocket] Warning: Error closing database: {e}")

        # Initialize components
        audio_buffer = AudioBuffer()
        interrupt_agent = InterruptAgent()
        last_interrupt_time = 0
        redis_store = get_redis_store()

        # Generate question (same logic as REST endpoint)
        from infrastructure.clients.resume_api_client import get_resume_analysis, get_job_analysis
        from infrastructure.clients.jobsearch_api_client import get_job_listing

        # Try to fetch resume/job data, but continue if external APIs are down
        resume_data = {}
        job_analysis_data = None
        job_data = None

        try:
            print(f"[WebSocket] Fetching resume data...")
            resume_data = await get_resume_analysis(str(session.resume_id)) or {}
            print(f"[WebSocket] Resume data fetched")

            # Fetch job analysis data for richer context (skill assessments, O*NET mappings)
            experience_id_to_use = session.experience_id  # User-selected experience

            # Fallback: If no experience_id in session, use first experience from resume
            if not experience_id_to_use and resume_data.get("structuredData"):
                experiences = resume_data.get("structuredData", {}).get("experiences", [])
                if experiences and len(experiences) > 0:
                    experience_id_to_use = experiences[0].get("id")
                    print(f"[WebSocket] No experience_id in session, using first experience: {experience_id_to_use}")

            if experience_id_to_use:
                print(f"[WebSocket] Fetching job analysis for experience {experience_id_to_use}...")
                job_analysis_data = await get_job_analysis(str(session.resume_id), str(experience_id_to_use))
                if job_analysis_data:
                    print(f"[WebSocket] Job analysis data fetched (with skill assessments)")
        except Exception as e:
            print(f"[WebSocket] Warning: Could not fetch resume/analysis data: {e}")
            # Continue with empty resume data

        try:
            if session.job_listing_id:
                print(f"[WebSocket] Fetching job listing data...")
                job_data = await get_job_listing(str(session.job_listing_id))
                print(f"[WebSocket] Job listing data fetched (includes description)")
        except Exception as e:
            print(f"[WebSocket] Warning: Could not fetch job data: {e}")
            # Continue with empty job data

        # Check if there are previous rounds - extract already-asked topics
        asked_topics = []
        if round_number > 1:
            # Query previous rounds to get their questions
            try:
                db_gen = get_db()
                db = next(db_gen)
                previous_rounds = db.query(SessionRound).filter(
                    SessionRound.session_id == session_id,
                    SessionRound.round_number < round_number,
                    SessionRound.status == "COMPLETED"
                ).all()

                # Extract keywords from previous questions (simple approach)
                common_tech = ["spring boot", "kubernetes", "docker", "terraform", "jenkins",
                               "aws", "azure", "gcp", "microservices", "api", "database",
                               "redis", "kafka", "mysql", "postgresql", "mongodb"]

                for prev_round in previous_rounds:
                    if prev_round.question_text:
                        question_lower = prev_round.question_text.lower()
                        for tech in common_tech:
                            if tech in question_lower:
                                asked_topics.append(tech)

                if asked_topics:
                    print(f"[WebSocket] Already asked about: {', '.join(set(asked_topics))}")
            except Exception as e:
                print(f"[WebSocket] Warning: Could not fetch previous rounds: {e}")

        initial_state = {
            "session_id": session_id,
            "user_id": session.user_id,
            "resume_data": resume_data,
            "job_analysis_data": job_analysis_data,  # Rich analysis with skill assessments
            "job_data": job_data,  # Job listing with description
            "interview_type": session.interview_type,
            "difficulty_level": session.difficulty_level,
            "interrupt_level": session.interrupt_level,
            "current_round": round_number,
            "max_rounds": session.total_rounds,
            "current_question_num": 0,
            "max_questions": 1,
            "question": "",
            "user_answer": "",
            "conversation_history": [],
            "score": 0.0,
            "asked_topics": list(set(asked_topics))  # Unique topics already covered
        }

        # Initialize Redis state for this round
        await redis_store.init_round(session_id, round_number)
        print(f"[Redis] Initialized conversation state")

        # Generate question
        result = await generate_question(initial_state)
        question_text = result["question"]

        # Generate TTS for question
        import tempfile
        question_audio_path = os.path.join(tempfile.gettempdir(), f"question_{session_id}_{round_number}.mp3")
        await text_to_speech(question_text, question_audio_path)

        # Read audio file and encode
        with open(question_audio_path, "rb") as f:
            question_audio_base64 = base64.b64encode(f.read()).decode()

        # Save initial question to Redis
        await redis_store.add_interaction(session_id, round_number, {
            "interaction_num": 1,
            "question": question_text
        })
        print(f"[Redis] Saved initial question")

        # Send question to client (client should pause recording during playback)
        await websocket.send_json({
            "type": "question",
            "text": question_text,
            "audio_base64": question_audio_base64,
            "pause_recording": True,  # Tell client to stop recording during playback
            "can_regenerate": True  # Tell client this is the initial question (can be regenerated)
        })

        # OUTER LOOP: Multi-turn conversation (keep asking follow-ups until Grok is satisfied)
        attempt_count = 0
        last_followup_question = None  # Track last question to prevent duplicates
        awaiting_first_answer = True  # Track if we're waiting for the first answer (regenerate allowed)

        while True:
            attempt_count += 1
            print(f"[WebSocket] Answer attempt #{attempt_count}")

            # Handle regenerate_question request (only before first answer)
            # Allow MULTIPLE regenerates with a loop
            if awaiting_first_answer:
                regenerate_loop = True
                first_audio_chunk = None  # Store first audio chunk if user starts recording
                while regenerate_loop:
                    try:
                        # Check for regenerate request with 2 second timeout (balance between user action time and responsiveness)
                        regen_data = await asyncio.wait_for(websocket.receive(), timeout=2.0)
                        if "text" in regen_data:
                            import json
                            try:
                                message_json = json.loads(regen_data["text"])
                                msg = message_json.get("text", "")
                                if msg == "regenerate_question":
                                    print(f"[WebSocket] Regenerating initial question...")

                                    # Remove last interaction from Redis
                                    await redis_store.remove_last_interaction(session_id, round_number)

                                    # Generate new question with same context
                                    result = await generate_question(initial_state)
                                    question_text = result["question"]

                                    # Generate TTS for new question
                                    question_audio_path = os.path.join(tempfile.gettempdir(), f"question_{session_id}_{round_number}_regen.mp3")
                                    await text_to_speech(question_text, question_audio_path)

                                    with open(question_audio_path, "rb") as f:
                                        question_audio_base64 = base64.b64encode(f.read()).decode()

                                    # Save new question to Redis
                                    await redis_store.add_interaction(session_id, round_number, {
                                        "interaction_num": 1,
                                        "question": question_text
                                    })
                                    print(f"[Redis] Saved regenerated question")

                                    # Send new question to client
                                    await websocket.send_json({
                                        "type": "question",
                                        "text": question_text,
                                        "audio_base64": question_audio_base64,
                                        "pause_recording": True,
                                        "can_regenerate": True,
                                        "is_regenerated": True
                                    })

                                    # Clean up temp file
                                    if os.path.exists(question_audio_path):
                                        os.remove(question_audio_path)

                                    print(f"[WebSocket] Sent regenerated question, looping back for another possible regenerate")
                                    attempt_count = 0  # Reset attempt counter
                                    # Continue inner loop - check for another regenerate
                                    continue
                                else:
                                    # Different control message, exit regenerate loop
                                    print(f"[WebSocket] Received control message: {msg}, exiting regenerate loop")
                                    regenerate_loop = False
                            except json.JSONDecodeError:
                                pass
                        elif "bytes" in regen_data:
                            # Audio data received - user started recording, exit regenerate loop
                            print(f"[WebSocket] User started recording, exiting regenerate loop")
                            first_audio_chunk = regen_data["bytes"]  # Save first chunk
                            regenerate_loop = False
                    except asyncio.TimeoutError:
                        # Timeout - no regenerate request, proceed with answer collection
                        print(f"[WebSocket] Regenerate timeout, proceeding to answer collection")
                        regenerate_loop = False

                # If we captured the first audio chunk, add it to the buffer
                if first_audio_chunk:
                    audio_buffer.add_chunk(first_audio_chunk)
                    print(f"[WebSocket] Added captured first audio chunk to buffer")

            # Collect user's answer using helper function
            try:
                full_transcript, last_interrupt_time = await collect_user_answer(
                    websocket=websocket,
                    session_id=session_id,
                    round_number=round_number,
                    question_text=question_text,
                    session=session,
                    audio_buffer=audio_buffer,
                    interrupt_agent=interrupt_agent,
                    last_interrupt_time=last_interrupt_time
                )
            except WebSocketDisconnect:
                print(f"[WebSocket] Client disconnected during answer collection")
                manager.disconnect(session_id)
                return
            except RuntimeError as e:
                if "websocket.send" in str(e):
                    print(f"[WebSocket] Client disconnected (RuntimeError)")
                    manager.disconnect(session_id)
                    return
                raise

            # Wait 2 seconds to ensure any pending transcription completes
            print(f"[WebSocket] Waiting 2s for final transcription to complete...")
            await asyncio.sleep(2.0)

            # Disable regeneration after first answer attempt
            if awaiting_first_answer:
                awaiting_first_answer = False
                print(f"[WebSocket] First answer received, regeneration disabled")

            # Log final transcript
            word_count = len(full_transcript.split())
            print(f"[WebSocket] Final transcript: {word_count} words - '{full_transcript[:100]}...'")

            # Check if answer is too short (likely captured TTS playback or incomplete answer)
            if word_count < 20:
                print(f"[WebSocket] Answer too short ({word_count} words), asking user to elaborate...")

                # Ask user to provide more detail (don't evaluate yet)
                retry_message = "Can you provide more detail? I need at least a few sentences to properly evaluate your answer."
                retry_audio_path = os.path.join(
                    tempfile.gettempdir(),
                    f"retry_{session_id}_{round_number}_{time.time()}.mp3"
                )
                await text_to_speech(retry_message, retry_audio_path)

                with open(retry_audio_path, "rb") as f:
                    retry_audio_base64 = base64.b64encode(f.read()).decode()

                # Send retry prompt (reuse same question)
                await websocket.send_json({
                    "type": "followup",
                    "feedback": "I need more detail to evaluate properly.",
                    "question": question_text,  # Same question
                    "audio_base64": retry_audio_base64,
                    "satisfaction_level": 0
                })

                if os.path.exists(retry_audio_path):
                    os.remove(retry_audio_path)

                # Reset buffers and continue loop (don't evaluate)
                audio_buffer.reset_for_next_question()
                print(f"[WebSocket] Retry prompt sent, waiting for more detailed answer...")
                continue

            # OLD CODE REPLACED BY HELPER FUNCTION (245 lines → 20 lines!)
            # Everything from line 457 to 686 is now in collect_user_answer()

            # Evaluate complete answer and decide: followup or final eval
            state_values = result.copy()
            state_values["user_answer"] = full_transcript
            state_values["interview_type"] = session.interview_type
            state_values["difficulty_level"] = session.difficulty_level
            state_values["current_round"] = round_number
            state_values["max_rounds"] = session.total_rounds

            eval_result = await evaluate_answer(state_values)

            # Update result with new conversation history (for next iteration if followup)
            result["conversation_history"] = eval_result.get("conversation_history", [])

            # Get Grok's decision
            needs_followup = eval_result.get("needs_followup", False)
            satisfaction_level = eval_result.get("satisfaction_level", 0)
            feedback_text = eval_result.get("feedback", "")
            followup_question_text = eval_result.get("followup_question", "")
            final_summary = eval_result.get("final_summary", "")

            print(f"[WebSocket] Grok decision: needs_followup={needs_followup}, satisfaction={satisfaction_level}")

            # Update Redis with answer and evaluation
            await redis_store.update_last_interaction(session_id, round_number, {
                "answer_transcript": full_transcript,
                "satisfaction_level": satisfaction_level,
                "needs_followup": needs_followup,
                "feedback": feedback_text,
                "followup_question": followup_question_text if needs_followup else None,
                "final_summary": final_summary if not needs_followup else None
            })
            print(f"[Redis] Updated interaction with evaluation")

            # Safety check: Prevent duplicate follow-ups
            if needs_followup and followup_question_text == last_followup_question:
                print(f"[WebSocket] ⚠️ Duplicate follow-up detected, forcing final evaluation instead")
                needs_followup = False  # Force CASE 2 (final evaluation)

            if needs_followup and followup_question_text:
                # CASE 1: Ask follow-up question and continue loop
                print(f"[WebSocket] Asking follow-up: {followup_question_text}")
                last_followup_question = followup_question_text

                # Generate TTS for feedback + followup question
                spoken_followup = f"{feedback_text} {followup_question_text}"
                followup_audio_path = os.path.join(
                    tempfile.gettempdir(),
                    f"followup_{session_id}_{round_number}_{time.time()}.mp3"
                )
                await text_to_speech(spoken_followup, followup_audio_path)

                # Read and encode audio
                with open(followup_audio_path, "rb") as f:
                    followup_audio_base64 = base64.b64encode(f.read()).decode()

                # Send follow-up to client
                await websocket.send_json({
                    "type": "followup",
                    "feedback": feedback_text,
                    "question": followup_question_text,
                    "audio_base64": followup_audio_base64,
                    "satisfaction_level": satisfaction_level
                })

                # Clean up temp file
                if os.path.exists(followup_audio_path):
                    os.remove(followup_audio_path)

                # Update question for next iteration
                question_text = followup_question_text

                # Add new interaction to Redis for followup question
                interaction_count = await redis_store.get_interaction_count(session_id, round_number)
                await redis_store.add_interaction(session_id, round_number, {
                    "interaction_num": interaction_count + 1,
                    "question": followup_question_text
                })
                print(f"[Redis] Saved followup question as interaction #{interaction_count + 1}")

                # Reset audio buffer for next answer
                audio_buffer.reset_for_next_question()

                print(f"[WebSocket] Follow-up sent, continuing to next answer (no draining)")

                # CONTINUE THE OUTER LOOP - collect next answer
                continue

            else:
                # CASE 2: Provide final summary and close
                print(f"[WebSocket] Providing final summary (score: {satisfaction_level})")

                # Generate spoken evaluation (detailed feedback)
                spoken_evaluation = final_summary if final_summary else f"Your score is {satisfaction_level} out of 100. {feedback_text}"

                # Generate TTS for detailed evaluation
                evaluation_audio_path = os.path.join(
                    tempfile.gettempdir(),
                    f"evaluation_{session_id}_{round_number}_{time.time()}.mp3"
                )
                await text_to_speech(spoken_evaluation, evaluation_audio_path)

                # Read and encode evaluation audio
                with open(evaluation_audio_path, "rb") as f:
                    evaluation_audio_base64 = base64.b64encode(f.read()).decode()

                # Send final evaluation with audio
                await websocket.send_json({
                    "type": "evaluation",
                    "score": satisfaction_level,
                    "feedback": final_summary if final_summary else feedback_text,
                    "is_satisfactory": satisfaction_level >= 70,
                    "audio_base64": evaluation_audio_base64
                })

                print(f"[WebSocket] Sent final evaluation with audio")

                # Clean up temp file
                if os.path.exists(evaluation_audio_path):
                    os.remove(evaluation_audio_path)

                # Save final score to Redis
                await redis_store.set_final_score(session_id, round_number, satisfaction_level)
                print(f"[Redis] Saved final score: {satisfaction_level}")

                # Persist conversation from Redis to PostgreSQL
                print(f"[WebSocket] Persisting conversation to database...")
                db_new = next(get_db())
                try:
                    await persist_round_to_database(session_id, round_number, db_new, redis_store)
                    print(f"[WebSocket] Successfully persisted to database")
                except Exception as persist_error:
                    print(f"[WebSocket] ERROR persisting to database: {persist_error}")
                    import traceback
                    print(traceback.format_exc())
                finally:
                    db_new.close()

                # Send simple completion message
                await websocket.send_json({
                    "type": "completion",
                    "message": "This interview round is now complete. You can review the detailed feedback above."
                })

                print(f"[WebSocket] Sent completion message")

                await websocket.close()
                manager.disconnect(session_id)
                break  # Exit outer loop - interview round complete

    except Exception as e:
        import traceback
        print(f"[WebSocket] ERROR: {e}")
        print(traceback.format_exc())
        try:
            await websocket.send_json({"type": "error", "message": str(e)})
            await websocket.close()
        except:
            pass
        manager.disconnect(session_id)
    finally:
        # Database connection already closed early to avoid timeout
        # No cleanup needed here
        pass
