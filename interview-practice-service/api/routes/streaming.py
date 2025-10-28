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
from infrastructure.websocket.vad import VoiceActivityDetector
from infrastructure.websocket.connection_manager import manager
from infrastructure.clients.openai_client import text_to_speech, transcribe_audio
from domain.agents.interrupt_agent import InterruptAgent
from domain.services.question_evaluation import generate_question, evaluate_answer


router = APIRouter(prefix="/ws/interview", tags=["websocket"])


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

        # Initialize components
        audio_buffer = AudioBuffer()
        vad = VoiceActivityDetector(silence_threshold_ms=10000)  # 10 seconds of silence to end speech
        interrupt_agent = InterruptAgent()
        last_interrupt_time = 0

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

        # Send question to client (client should pause recording during playback)
        await websocket.send_json({
            "type": "question",
            "text": question_text,
            "audio_base64": question_audio_base64,
            "pause_recording": True  # Tell client to stop recording during playback
        })

        # Listen for user's answer
        full_transcript = ""
        start_time = time.time()
        chunks_received = 0
        failed_transcriptions = 0
        MAX_FAILED_TRANSCRIPTIONS = 3
        consecutive_empty_transcriptions = 0  # Track consecutive empty results (user stopped speaking)
        MAX_EMPTY_BEFORE_END = 1  # End after 1 empty transcription (10s of silence) - faster detection

        # Set a maximum recording time (3 minutes - enough for a thorough answer)
        MAX_RECORDING_TIME = 180

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
                    # No data received for 1 second, check if speech ended
                    if vad.is_speech_ended():
                        print(f"[WebSocket] Speech ended (timeout + VAD)")
                        break
                    continue
                except RuntimeError as e:
                    # Client disconnected
                    if "disconnect" in str(e).lower():
                        print(f"[WebSocket] Client disconnected gracefully")
                        break
                    raise

                if "bytes" in data:
                    audio_chunk = data["bytes"]
                    audio_buffer.append(audio_chunk)
                    chunks_received += 1

                    # Process VAD on every chunk
                    is_speech = vad.process_frame(audio_chunk)

                    # Check if user stopped speaking (10s silence detected by VAD)
                    if vad.is_speech_ended():
                        print(f"[WebSocket] Speech ended (VAD: 10s silence detected)")
                        break

                    # Transcribe every 10 seconds of audio (longer chunks are more reliable for WebM)
                    buffer_duration = audio_buffer.duration_seconds()
                    if buffer_duration >= 10.0:
                        print(f"[WebSocket] Buffer reached {buffer_duration:.1f}s, checking speech...")

                        # Check if buffer contains actual speech using VAD
                        # If user has been speaking, transcribe. If pure silence, skip.
                        if not vad.is_speaking:
                            print(f"[WebSocket] Buffer contains only silence, skipping transcription")
                            audio_buffer.clear()
                            continue

                        print(f"[WebSocket] Speech detected in buffer, transcribing...")

                        # Save ACCUMULATED buffer to temp file
                        # Use get_accumulated_with_header() which prepends stored header if available
                        temp_audio_path = os.path.join(
                            tempfile.gettempdir(),
                            f"accumulated_{session_id}_{round_number}_{time.time()}.webm"
                        )
                        accumulated_data = audio_buffer.get_accumulated_with_header()
                        has_stored_header = audio_buffer.stored_header is not None
                        print(f"[WebSocket] Accumulated audio size: {len(accumulated_data)} bytes ({len(audio_buffer.accumulated_chunks)} chunks, header: {has_stored_header})")

                        with open(temp_audio_path, "wb") as f:
                            f.write(accumulated_data)

                        # Clear sliding window buffer (but keep accumulated_chunks for next transcription)
                        audio_buffer.clear()
                        print(f"[WebSocket] Cleared sliding window buffer")

                        # Transcribe chunk (WebM will be converted to WAV automatically)
                        try:
                            chunk_transcript = await transcribe_audio(temp_audio_path)

                            # Filter out Whisper hallucinations (common silence patterns)
                            if chunk_transcript:
                                cleaned = chunk_transcript.strip().lower()

                                # Common Whisper hallucinations during silence
                                hallucinations = [
                                    ".", "..", "...", ". .", ". . .", "thank you", "thanks",
                                    "bye", "goodbye", "okay", "ok", "um", "uh", "hmm"
                                ]

                                # Check if entire transcript is just hallucination
                                is_hallucination = cleaned in hallucinations or all(c in '. ' for c in cleaned)

                                if is_hallucination:
                                    print(f"[WebSocket] Filtered Whisper hallucination: '{chunk_transcript}'")
                                    chunk_transcript = None  # Treat as silence

                            if chunk_transcript:  # Only process if we got actual text (not hallucination)
                                full_transcript += " " + chunk_transcript
                                failed_transcriptions = 0  # Reset failure counter
                                consecutive_empty_transcriptions = 0  # Reset empty counter (user is speaking)

                                # CRITICAL: After first successful transcription, store WebM header for reuse
                                # This solves the "EBML header parsing failed" issue on subsequent chunks
                                if audio_buffer.stored_header is None:
                                    print(f"[WebSocket] First successful transcription - extracting WebM header")
                                    audio_buffer.store_header_from_file(temp_audio_path)

                                # CRITICAL: Clear accumulated chunks to prevent re-transcribing same audio
                                # We keep the stored_header (reused for next cycle)
                                # This prevents duplicate transcriptions and growing memory usage
                                audio_buffer.accumulated_chunks = []
                                print(f"[WebSocket] Cleared accumulated chunks (keeping header for next cycle)")

                                # Send live transcript to client
                                await websocket.send_json({
                                    "type": "transcript",
                                    "text": chunk_transcript
                                })

                                # Check for interrupt ONLY if we have meaningful transcript (>10 words)
                                word_count = len(full_transcript.split())
                                did_interrupt = False  # Track if we actually interrupted

                                if word_count > 10:
                                    current_time = time.time()
                                    duration_seconds = current_time - start_time
                                    time_since_last_interrupt = current_time - last_interrupt_time

                                    # Check for interrupt (every 30s minimum between interrupts - give user time to incorporate feedback)
                                    if time_since_last_interrupt >= 30.0:
                                        print(f"[WebSocket] Checking for interrupt (transcript: {word_count} words)")

                                        # Tell client AI is thinking (user can see indicator)
                                        await websocket.send_json({
                                            "type": "ai_thinking",
                                            "message": "AI is analyzing your answer..."
                                        })

                                        # Analyze for interrupt
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
                                            did_interrupt = True  # Mark that we interrupted

                                            # Reset accumulator - client will restart MediaRecorder with fresh WebM stream
                                            # This is CRITICAL: new MediaRecorder = new WebM header
                                            audio_buffer.clear()
                                            audio_buffer.reset_accumulator()
                                            print(f"[WebSocket] Cleared buffer and reset accumulator (client will restart MediaRecorder)")

                                            # Generate interrupt TTS
                                            interrupt_audio_path = os.path.join(
                                                tempfile.gettempdir(),
                                                f"interrupt_{session_id}_{round_number}_{time.time()}.mp3"
                                            )
                                            await text_to_speech(decision["feedback_text"], interrupt_audio_path)

                                            with open(interrupt_audio_path, "rb") as f:
                                                interrupt_audio_base64 = base64.b64encode(f.read()).decode()

                                            # Send interrupt to client (restart recording after playback)
                                            await websocket.send_json({
                                                "type": "interrupt",
                                                "text": decision["feedback_text"],
                                                "reason": decision["reason"],
                                                "interrupt_type": decision["interrupt_type"],
                                                "audio_base64": interrupt_audio_base64,
                                                "restart_recording": True  # Tell client to stop/start fresh after interrupt
                                            })

                                            last_interrupt_time = current_time

                                            # Reset timer and VAD after interrupt so user can continue answering
                                            start_time = time.time()
                                            vad.reset()  # Reset VAD to start fresh silence detection
                                            print(f"[WebSocket] Reset recording timer and VAD after interrupt")
                                        else:
                                            # AI decided NOT to interrupt
                                            # Just update the timestamp
                                            last_interrupt_time = current_time
                                            print(f"[WebSocket] AI decided not to interrupt, will check again in 30s")

                                            # DON'T reset start_time - let the full answer continue until naturally complete
                            else:
                                # Empty transcription - could be silence or hallucination filtered
                                if full_transcript:
                                    # We already have some transcript, empty result means user stopped speaking
                                    consecutive_empty_transcriptions += 1
                                    print(f"[WebSocket] Empty transcription #{consecutive_empty_transcriptions} (likely silence)")

                                    # If we get empty transcription, user is done (10s silence)
                                    if consecutive_empty_transcriptions >= MAX_EMPTY_BEFORE_END:
                                        print(f"[WebSocket] User stopped speaking ({MAX_EMPTY_BEFORE_END} empty transcription = 10s silence)")
                                        break
                                else:
                                    # No transcript yet, this is a real failure
                                    failed_transcriptions += 1
                                    print(f"[WebSocket] Failed transcription {failed_transcriptions}/{MAX_FAILED_TRANSCRIPTIONS}")

                                    # If too many failures, stop trying
                                    if failed_transcriptions >= MAX_FAILED_TRANSCRIPTIONS:
                                        print(f"[WebSocket] Too many failed transcriptions, ending")
                                        break

                        except Exception as e:
                            print(f"Transcription error: {e}")
                            failed_transcriptions += 1
                            if failed_transcriptions >= MAX_FAILED_TRANSCRIPTIONS:
                                print(f"[WebSocket] Too many errors, ending")
                                break

                        # Clean up temp file
                        if os.path.exists(temp_audio_path):
                            os.remove(temp_audio_path)

                        # Buffer already cleared after successful transcription (line 253)
                        # No need to clear again here

                elif "text" in data:
                    # Client sent control message
                    msg = data["text"]
                    if msg == "end_answer":
                        print(f"[WebSocket] Client requested end")
                        break

        except WebSocketDisconnect:
            print(f"Client disconnected from session {session_id}")
            manager.disconnect(session_id)
            return

        # Evaluate complete answer
        state_values = result.copy()
        state_values["user_answer"] = full_transcript
        state_values["interview_type"] = session.interview_type
        state_values["difficulty_level"] = session.difficulty_level
        state_values["current_round"] = round_number
        state_values["max_rounds"] = session.total_rounds

        eval_result = await evaluate_answer(state_values)

        # Extract reasoning and suggestions from conversation history
        reasoning = ""
        suggestions = []
        for msg in eval_result.get("conversation_history", []):
            if msg.get("type") == "evaluation":
                # Format: "Score: 0.75 | Reasoning text here"
                content = msg.get("content", "")
                if " | " in content:
                    reasoning = content.split(" | ", 1)[1]

        # Calculate score (0-100 scale for display)
        cumulative_scores = eval_result.get("cumulative_scores", [])
        score = cumulative_scores[-1] * 100 if cumulative_scores else 0.0

        # Build feedback text
        feedback = f"**Evaluation:**\n\n{reasoning}\n\n"
        if not eval_result.get("is_satisfactory", False):
            feedback += "**Areas for improvement:**\n"
            feedback += "- Provide more specific examples from your experience\n"
            feedback += "- Explain the impact and measurable outcomes\n"
            feedback += "- Structure your answer with more clarity\n"
        else:
            feedback += "**Good points:**\n"
            feedback += "- You provided relevant examples\n"
            feedback += "- Your thought process was clear\n"

        # Generate spoken evaluation (detailed feedback)
        spoken_evaluation = f"Your score is {score:.0f} out of 100. {reasoning}"

        # Generate TTS for detailed evaluation
        evaluation_audio_path = os.path.join(
            tempfile.gettempdir(),
            f"evaluation_{session_id}_{round_number}_{time.time()}.mp3"
        )
        await text_to_speech(spoken_evaluation, evaluation_audio_path)

        # Read and encode evaluation audio
        with open(evaluation_audio_path, "rb") as f:
            evaluation_audio_base64 = base64.b64encode(f.read()).decode()

        # Send detailed evaluation with audio
        await websocket.send_json({
            "type": "evaluation",
            "score": score,
            "feedback": feedback,
            "is_satisfactory": eval_result.get("is_satisfactory", False),
            "audio_base64": evaluation_audio_base64  # Add audio to evaluation message
        })

        print(f"[WebSocket] Sent evaluation with audio")

        # Clean up temp file
        if os.path.exists(evaluation_audio_path):
            os.remove(evaluation_audio_path)

        # Send simple completion message (no audio needed, evaluation already spoken)
        await websocket.send_json({
            "type": "completion",
            "message": "This interview round is now complete. You can review the detailed feedback above."
        })

        print(f"[WebSocket] Sent completion message")

        await websocket.close()
        manager.disconnect(session_id)

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
        db.close()
