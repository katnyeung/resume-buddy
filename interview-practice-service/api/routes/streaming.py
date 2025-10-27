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
from graph.interview_graph import generate_question, evaluate_answer


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
        vad = VoiceActivityDetector(silence_threshold_ms=800)
        interrupt_agent = InterruptAgent()
        last_interrupt_time = 0
        last_transcription_time = time.time()  # Track when we last got transcription
        SILENCE_TIMEOUT = 15  # If no transcription for 15 seconds, assume user finished

        # Generate question (same logic as REST endpoint)
        from infrastructure.clients.resume_api_client import get_resume_analysis
        from infrastructure.clients.jobsearch_api_client import get_job_listing

        # Try to fetch resume/job data, but continue if external APIs are down
        resume_data = {}
        job_data = None

        try:
            print(f"[WebSocket] Fetching resume data...")
            resume_data = await get_resume_analysis(str(session.resume_id)) or {}
            print(f"[WebSocket] Resume data fetched")
        except Exception as e:
            print(f"[WebSocket] Warning: Could not fetch resume data: {e}")
            # Continue with empty resume data

        try:
            if session.job_listing_id:
                print(f"[WebSocket] Fetching job data...")
                job_data = await get_job_listing(str(session.job_listing_id))
                print(f"[WebSocket] Job data fetched")
        except Exception as e:
            print(f"[WebSocket] Warning: Could not fetch job data: {e}")
            # Continue with empty job data

        initial_state = {
            "session_id": session_id,
            "user_id": session.user_id,
            "resume_data": resume_data,
            "job_data": job_data,
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
            "score": 0.0
        }

        from graph.interview_graph import build_interview_graph
        from infrastructure.redis_client import get_async_redis_checkpointer

        async with get_async_redis_checkpointer() as checkpointer:
            graph = build_interview_graph(checkpointer)
            thread_id = f"{session_id}_round_{round_number}"
            config = {"configurable": {"thread_id": thread_id}}

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
            is_recording_active = True  # Flag to track if we should accept audio chunks

            # Set a maximum recording time (3 minutes - enough for a thorough answer)
            MAX_RECORDING_TIME = 180

            try:
                while True:
                    # Check if max time exceeded
                    elapsed = time.time() - start_time
                    if elapsed > MAX_RECORDING_TIME:
                        print(f"[WebSocket] Max recording time reached, ending answer")
                        break

                    # Check if user stopped speaking (no transcription for SILENCE_TIMEOUT seconds)
                    # ONLY check if we're actively recording (not during interrupts)
                    if is_recording_active:
                        time_since_last_transcription = time.time() - last_transcription_time
                        if time_since_last_transcription > SILENCE_TIMEOUT and full_transcript:
                            print(f"[WebSocket] No transcription for {SILENCE_TIMEOUT}s, user finished speaking")
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
                        # Only accept audio chunks if recording is active
                        if not is_recording_active:
                            print(f"[WebSocket] Ignoring chunk during interrupt (recording inactive)")
                            continue

                        audio_chunk = data["bytes"]
                        audio_buffer.append(audio_chunk)
                        chunks_received += 1

                        # Process VAD
                        is_speech = vad.process_frame(audio_chunk)

                        # Transcribe every 10 seconds of audio (longer chunks are more reliable for WebM)
                        buffer_duration = audio_buffer.duration_seconds()
                        if buffer_duration >= 10.0:
                            print(f"[WebSocket] Buffer reached {buffer_duration:.1f}s, transcribing...")

                            # Save buffer to temp file for transcription
                            temp_audio_path = os.path.join(
                                tempfile.gettempdir(),
                                f"chunk_{session_id}_{round_number}_{time.time()}.webm"
                            )
                            buffer_data = audio_buffer.get_audio()
                            print(f"[WebSocket] Buffer size: {len(buffer_data)} bytes")

                            with open(temp_audio_path, "wb") as f:
                                f.write(buffer_data)

                            # IMPORTANT: Clear buffer immediately after saving to prevent duplicate transcriptions
                            # We have the data saved to disk, buffer can be cleared now
                            audio_buffer.clear()
                            print(f"[WebSocket] Cleared buffer after saving to disk")

                            # Transcribe chunk (skip validation for streaming fragments)
                            try:
                                chunk_transcript = await transcribe_audio(temp_audio_path, skip_validation=True)

                                if chunk_transcript:  # Only process if we got actual text
                                    full_transcript += " " + chunk_transcript
                                    failed_transcriptions = 0  # Reset failure counter
                                    last_transcription_time = time.time()  # Update last transcription time (for VAD)
                                    print(f"[WebSocket] Updated last transcription time (for silence detection)")

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

                                                # Clear buffer - client will restart MediaRecorder with fresh WebM stream
                                                audio_buffer.clear()
                                                print(f"[WebSocket] Cleared buffer (interrupting - client will restart with fresh WebM stream)")

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

                                                # Stop accepting audio chunks during interrupt
                                                is_recording_active = False
                                                print(f"[WebSocket] Disabled audio chunk acceptance during interrupt")

                                                # Reset timer after interrupt so user can continue answering
                                                start_time = time.time()
                                                print(f"[WebSocket] Reset recording timer after interrupt")
                                            else:
                                                # AI decided NOT to interrupt
                                                # Just update the timestamp
                                                last_interrupt_time = current_time
                                                print(f"[WebSocket] AI decided not to interrupt, will check again in 30s")

                                                # DON'T reset start_time - let the full answer continue until naturally complete

                                    # ONLY send silent restart if we didn't just interrupt
                                    # (if we interrupted, client will restart via the interrupt message)
                                    if not did_interrupt:
                                        is_recording_active = False
                                        await websocket.send_json({
                                            "type": "silent_restart",
                                            "message": "Continue..."
                                        })
                                        print(f"[WebSocket] Sent silent_restart to keep WebM stream fresh")
                                else:
                                    # Empty transcription - could be silence
                                    if full_transcript:
                                        # We already have some transcript, so empty result likely means user stopped speaking
                                        # Let the 15-second silence timeout handle this, don't count as failure
                                        print(f"[WebSocket] Empty transcription (likely silence), letting timeout handle it")
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

                        # Check if user stopped speaking
                        if vad.is_speech_ended():
                            # User finished answering
                            break

                    elif "text" in data:
                        # Client sent control message
                        msg = data["text"]
                        if msg == "end_answer":
                            print(f"[WebSocket] Client requested end")
                            break
                        elif msg == "recording_restarted":
                            # Client has restarted MediaRecorder, we can accept chunks again
                            is_recording_active = True
                            last_transcription_time = time.time()  # Reset silence timer
                            print(f"[WebSocket] Recording restarted, accepting audio chunks again")

                    # Check VAD after each chunk
                    if chunks_received > 30 and vad.is_speech_ended():  # After ~1 second
                        print(f"[WebSocket] Speech ended (VAD detected silence)")
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

            # Send final evaluation to client
            await websocket.send_json({
                "type": "evaluation",
                "score": score,
                "feedback": feedback,
                "is_satisfactory": eval_result.get("is_satisfactory", False)
            })

            # Generate and send completion message with TTS
            completion_message = f"Thank you for completing this interview round! Your score is {score:.0f} out of 100. "
            if eval_result.get("is_satisfactory", False):
                completion_message += "Great job! You provided relevant examples and clear explanations. "
            else:
                completion_message += "You're on the right track. Focus on providing more specific examples and measurable outcomes in your next practice session. "

            completion_message += "Please review the detailed feedback on your screen. This session is now complete."

            # Generate TTS for completion message
            completion_audio_path = os.path.join(
                tempfile.gettempdir(),
                f"completion_{session_id}_{round_number}_{time.time()}.mp3"
            )
            await text_to_speech(completion_message, completion_audio_path)

            # Read and encode completion audio
            with open(completion_audio_path, "rb") as f:
                completion_audio_base64 = base64.b64encode(f.read()).decode()

            # Send completion message with audio
            await websocket.send_json({
                "type": "completion",
                "message": completion_message,
                "audio_base64": completion_audio_base64
            })

            print(f"[WebSocket] Sent completion message with audio")

            # Save state to Redis for later round completion
            await graph.aupdate_state(config, eval_result, as_node="evaluate_answer")

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
