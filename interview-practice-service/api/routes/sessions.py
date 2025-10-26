"""Interview session API endpoints."""
import os
import tempfile
from datetime import datetime, date, time, timedelta
from typing import Optional
from fastapi import APIRouter, UploadFile, File, HTTPException, Depends
from sqlalchemy.orm import Session
from api.models import (
    CreateSessionRequest,
    CreateSessionResponse,
    SessionQuestionResponse,
    SessionAnswerResponse,
    SessionCompleteResponse
)
from domain.services.session_manager import SessionManager
from domain.models import InterviewSession, SessionRound
from infrastructure.database import get_db
from infrastructure.redis_client import get_async_redis_checkpointer
from infrastructure.clients.openai_client import transcribe_audio, text_to_speech
from infrastructure.clients import resume_api_client, jobsearch_api_client
from graph.interview_graph import build_interview_graph


router = APIRouter(prefix="/api/interview", tags=["interview"])
AUDIO_STORAGE_PATH = os.getenv("AUDIO_STORAGE_PATH", "./audio_files")


@router.post("/sessions/create", response_model=CreateSessionResponse)
async def create_scheduled_session(
    request: CreateSessionRequest,
    db: Session = Depends(get_db)
):
    """
    Create a new scheduled interview practice session.

    This endpoint:
    1. Validates user has sufficient credits (50 credits)
    2. Deducts credits from user account
    3. Creates session record in database
    4. Sends email reminder to user with session link
    5. Returns session ID and confirmation
    """
    # 1. Check user credits
    try:
        credits_info = await resume_api_client.check_credits(request.user_id)
        available_credits = credits_info.get("availableCredits", 0)

        if available_credits < 50:
            raise HTTPException(
                status_code=400,
                detail=f"Insufficient credits. You have {available_credits} credits, but 50 are required for an interview session."
            )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"Failed to check credits: {str(e)}"
        )

    # 2. Fetch job listing details (for email and context)
    job_title = "General Interview"
    company_name = ""
    if request.job_listing_id:
        try:
            job_data = await jobsearch_api_client.get_job_listing(request.job_listing_id)
            if job_data:
                job_title = job_data.get("jobTitle", "General Interview")
                company_name = job_data.get("companyName", "")
        except Exception as e:
            # Non-critical, continue without job details
            print(f"Warning: Could not fetch job listing: {e}")

    # 4. Parse date and time
    try:
        scheduled_date = datetime.strptime(request.scheduled_date, "%Y-%m-%d").date()
        scheduled_time = datetime.strptime(request.scheduled_time, "%H:%M:%S").time()
    except ValueError as e:
        raise HTTPException(
            status_code=400,
            detail=f"Invalid date/time format. Use YYYY-MM-DD for date and HH:MM:SS for time. Error: {str(e)}"
        )

    # 5. Create session in database (master record)
    try:
        session = InterviewSession(
            user_id=request.user_id,
            resume_id=request.resume_id,
            job_listing_id=request.job_listing_id,
            interview_type=request.interview_type.value,
            difficulty_level=request.difficulty_level.value,
            interrupt_level=request.interrupt_level.value,
            scheduled_date=scheduled_date,
            scheduled_time=scheduled_time,
            timezone=request.timezone,
            total_rounds=request.total_rounds,
            round_interval=request.round_interval.value,
            status="SCHEDULED",
            credits_deducted=50
        )

        db.add(session)
        db.flush()  # Get session ID without committing yet

        # 6. Deduct credits (now we have session ID)
        try:
            await resume_api_client.deduct_credits(
                user_id=str(request.user_id),
                amount=50,
                job_id=str(session.id),
                reason="Interview Practice Session"
            )
        except Exception as e:
            db.rollback()
            raise HTTPException(
                status_code=500,
                detail=f"Failed to deduct credits: {str(e)}"
            )

        # 7. Create detail records for each round
        interval_days = {
            "DAILY": 1,
            "EVERY_2_DAYS": 2,
            "WEEKLY": 7
        }
        days = interval_days.get(request.round_interval.value, 1)

        for i in range(1, request.total_rounds + 1):
            round_date = scheduled_date + timedelta(days=(i - 1) * days)
            round_record = SessionRound(
                session_id=session.id,
                round_number=i,
                scheduled_date=round_date,
                scheduled_time=scheduled_time,
                status="PENDING"
            )
            db.add(round_record)

        db.commit()
        db.refresh(session)

    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=500,
            detail=f"Failed to create session: {str(e)}"
        )

    # 7. Send email for Round 1 only
    scheduled_datetime_str = f"{scheduled_date.strftime('%Y-%m-%d')} at {scheduled_time.strftime('%H:%M')} {request.timezone}"

    email_subject = f"🎯 Interview Practice Round 1 - {job_title}"
    email_body = f"""
Hi,

Your interview practice session is ready! This is Round 1 of {session.total_rounds}.

📅 Session Details:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Job Position: {job_title}{' at ' + company_name if company_name else ''}
• Round 1 Scheduled For: {scheduled_datetime_str}
• Total Rounds: {session.total_rounds} (spaced over {request.round_interval.value.replace('_', ' ').lower()})
• Coaching Level: {session.interrupt_level}

🎤 Start Round 1:
Click the link below whenever you're ready:
https://resumebuddy.cv/interview/session/{session.id}/round/1

💡 Quick Tips:
• Find a quiet space
• Test your microphone
• Have your resume ready for reference
• Take your time - practice at your own pace!

📬 What happens next?
After completing Round 1, you'll receive an email for Round 2 on the next scheduled date.

Good luck! You've got this 💪

Best regards,
Resume Buddy Team
    """

    try:
        await resume_api_client.send_email(
            user_id=str(request.user_id),
            subject=email_subject,
            body=email_body
        )

        # Mark email as sent for Round 1
        first_round = db.query(SessionRound).filter(
            SessionRound.session_id == session.id,
            SessionRound.round_number == 1
        ).first()

        if first_round:
            first_round.email_sent_at = datetime.utcnow()
            db.commit()

    except Exception as e:
        # Non-critical - session still created
        print(f"Warning: Could not send email: {e}")

    # 8. Return success response
    return CreateSessionResponse(
        session_id=str(session.id),
        status="SCHEDULED",
        message=f"Session created with {session.total_rounds} rounds! Check your email for Round 1 practice link.",
        scheduled_datetime=scheduled_datetime_str
    )


# ==================== ROUND PRACTICE ENDPOINTS ====================

@router.get("/sessions/{session_id}/rounds/{round_number}/start")
async def start_round(
    session_id: str,
    round_number: int,
    db: Session = Depends(get_db)
):
    """
    Start a specific round of interview practice.

    This endpoint:
    1. Validates session and round exist
    2. Marks round as IN_PROGRESS
    3. Initializes Redis/LangGraph state for this round
    4. Generates first question with Grok LLM
    5. Generates TTS audio for question
    6. Returns question text + audio URL
    """
    # 1. Fetch session
    session = db.query(InterviewSession).filter(
        InterviewSession.id == session_id
    ).first()

    if not session:
        raise HTTPException(status_code=404, detail="Session not found")

    # 2. Fetch round
    round = db.query(SessionRound).filter(
        SessionRound.session_id == session_id,
        SessionRound.round_number == round_number
    ).first()

    if not round:
        raise HTTPException(status_code=404, detail=f"Round {round_number} not found")

    # 3. Validate round can be started
    if round.status == "COMPLETED":
        raise HTTPException(status_code=400, detail="Round already completed")

    if round.status == "IN_PROGRESS":
        raise HTTPException(status_code=400, detail="Round already in progress")

    # Check previous round is completed (if not round 1)
    if round_number > 1:
        previous_round = db.query(SessionRound).filter(
            SessionRound.session_id == session_id,
            SessionRound.round_number == round_number - 1
        ).first()

        if not previous_round or previous_round.status != "COMPLETED":
            raise HTTPException(
                status_code=400,
                detail=f"Previous round (Round {round_number - 1}) must be completed first"
            )

    # 4. Mark round as IN_PROGRESS
    round.status = "IN_PROGRESS"
    round.started_at = datetime.utcnow()
    db.commit()

    # 5. Fetch resume and job context
    try:
        resume_data = await resume_api_client.get_resume_analysis(session.resume_id)
    except Exception as e:
        resume_data = {"resume_id": session.resume_id, "summary": "Resume data unavailable"}

    job_data = None
    if session.job_listing_id:
        try:
            job_data = await jobsearch_api_client.get_job_listing(session.job_listing_id)
        except Exception as e:
            job_data = None

    # 6. Initialize LangGraph state in Redis
    async with get_async_redis_checkpointer() as checkpointer:
        graph = build_interview_graph(checkpointer)

        # Use session_id + round_number as thread_id for isolation
        thread_id = f"{session_id}_round_{round_number}"

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
            "max_questions": 1,  # One question per round
            "question": "",
            "user_answer": "",
            "conversation_history": [],
            "score": 0.0
        }

        config = {"configurable": {"thread_id": thread_id}}

        # Generate question
        from graph.interview_graph import generate_question
        result = await generate_question(initial_state)

        # Save state to Redis
        await graph.aupdate_state(config, result, as_node="generate_question")

        question_text = result["question"]

        # 7. Generate TTS for question
        question_audio_path = os.path.join(
            AUDIO_STORAGE_PATH,
            str(session_id),
            f"round_{round_number}_question.mp3"
        )
        os.makedirs(os.path.dirname(question_audio_path), exist_ok=True)
        await text_to_speech(question_text, question_audio_path)

        # 8. Return question
        return {
            "session_id": session_id,
            "round_number": round_number,
            "status": "IN_PROGRESS",
            "question_text": question_text,
            "question_audio_url": question_audio_path,
            "started_at": round.started_at.isoformat()
        }


@router.post("/sessions/{session_id}/rounds/{round_number}/answer")
async def submit_round_answer(
    session_id: str,
    round_number: int,
    audio: UploadFile = File(...),
    db: Session = Depends(get_db)
):
    """
    Submit audio answer for a round.

    This endpoint:
    1. Validates round is IN_PROGRESS
    2. Transcribes audio (STT)
    3. Evaluates answer with Grok LLM
    4. Stores evaluation in Redis
    5. Returns feedback text + audio
    """
    # 1. Fetch session and round
    session = db.query(InterviewSession).filter(
        InterviewSession.id == session_id
    ).first()

    if not session:
        raise HTTPException(status_code=404, detail="Session not found")

    round = db.query(SessionRound).filter(
        SessionRound.session_id == session_id,
        SessionRound.round_number == round_number
    ).first()

    if not round:
        raise HTTPException(status_code=404, detail=f"Round {round_number} not found")

    if round.status != "IN_PROGRESS":
        raise HTTPException(status_code=400, detail="Round is not in progress")

    # 2. Save uploaded audio to temp file
    with tempfile.NamedTemporaryFile(delete=False, suffix=".mp3") as temp_audio:
        content = await audio.read()
        temp_audio.write(content)
        temp_audio_path = temp_audio.name

    try:
        # 3. STT: Audio → Text
        answer_text = await transcribe_audio(temp_audio_path)

        # 4. Get LangGraph state from Redis
        async with get_async_redis_checkpointer() as checkpointer:
            graph = build_interview_graph(checkpointer)
            thread_id = f"{session_id}_round_{round_number}"
            config = {"configurable": {"thread_id": thread_id}}

            # Get current state
            current_state = await graph.aget_state(config)
            state_values = current_state.values

            # Enrich state with session data (in case Redis state is missing fields)
            if "interview_type" not in state_values:
                state_values["interview_type"] = session.interview_type
            if "difficulty_level" not in state_values:
                state_values["difficulty_level"] = session.difficulty_level
            if "current_round" not in state_values:
                state_values["current_round"] = round_number
            if "max_rounds" not in state_values:
                state_values["max_rounds"] = session.total_rounds

            # Update with user answer
            state_values["user_answer"] = answer_text

            # Evaluate answer
            from graph.interview_graph import evaluate_answer
            eval_result = await evaluate_answer(state_values)

            # Ensure critical fields are preserved in the update
            eval_result["interview_type"] = session.interview_type
            eval_result["difficulty_level"] = session.difficulty_level
            eval_result["current_round"] = round_number
            eval_result["max_rounds"] = session.total_rounds

            # Save evaluation to Redis
            await graph.aupdate_state(config, eval_result, as_node="evaluate_answer")

            feedback_text = eval_result.get("feedback", "Answer recorded.")
            score = eval_result.get("score", 0.0)

            # 5. Generate TTS for feedback
            feedback_audio_path = os.path.join(
                AUDIO_STORAGE_PATH,
                str(session_id),
                f"round_{round_number}_feedback.mp3"
            )
            os.makedirs(os.path.dirname(feedback_audio_path), exist_ok=True)
            await text_to_speech(feedback_text, feedback_audio_path)

            # 6. Return feedback
            return {
                "session_id": session_id,
                "round_number": round_number,
                "answer_text": answer_text,
                "feedback_text": feedback_text,
                "feedback_audio_url": feedback_audio_path,
                "score": score,
                "next_action": "COMPLETE"  # Signal to call complete endpoint
            }

    finally:
        # Cleanup temp file
        os.unlink(temp_audio_path)


@router.post("/sessions/{session_id}/rounds/{round_number}/complete")
async def complete_round(
    session_id: str,
    round_number: int,
    db: Session = Depends(get_db)
):
    """
    Complete a round and persist data from Redis to PostgreSQL.

    This endpoint:
    1. Extracts data from Redis/LangGraph state
    2. Saves to interview_session_round table
    3. Updates master session (completed_rounds++, overall_score)
    4. Marks round as COMPLETED
    5. Clears Redis cache for this round
    """
    # 1. Fetch session and round
    session = db.query(InterviewSession).filter(
        InterviewSession.id == session_id
    ).first()

    if not session:
        raise HTTPException(status_code=404, detail="Session not found")

    round = db.query(SessionRound).filter(
        SessionRound.session_id == session_id,
        SessionRound.round_number == round_number
    ).first()

    if not round:
        raise HTTPException(status_code=404, detail=f"Round {round_number} not found")

    if round.status == "COMPLETED":
        raise HTTPException(status_code=400, detail="Round already completed")

    # 2. Extract data from Redis
    async with get_async_redis_checkpointer() as checkpointer:
        graph = build_interview_graph(checkpointer)
        thread_id = f"{session_id}_round_{round_number}"
        config = {"configurable": {"thread_id": thread_id}}

        try:
            state = await graph.aget_state(config)
            state_values = state.values

            # Extract data
            question_text = state_values.get("question", "")
            answer_text = state_values.get("user_answer", "")
            score = state_values.get("score", 0.0)
            feedback = state_values.get("feedback", "")
            evaluation_json = {
                "conversation_history": state_values.get("conversation_history", []),
                "interrupt_level": state_values.get("interrupt_level", ""),
                "score": score
            }

        except Exception as e:
            raise HTTPException(
                status_code=500,
                detail=f"Failed to retrieve round data from Redis: {str(e)}"
            )

    # 3. Save to database
    try:
        # Update round with data
        round.question_text = question_text
        round.answer_text = answer_text
        round.score = score
        round.feedback_text = feedback
        round.evaluation_json = evaluation_json
        round.status = "COMPLETED"
        round.completed_at = datetime.utcnow()

        # Audio URLs (already saved during start/answer)
        round.question_audio_url = os.path.join(
            AUDIO_STORAGE_PATH, str(session_id), f"round_{round_number}_question.mp3"
        )
        round.feedback_audio_url = os.path.join(
            AUDIO_STORAGE_PATH, str(session_id), f"round_{round_number}_feedback.mp3"
        )

        # Update master session
        session.completed_rounds += 1

        # Recalculate overall score (average of all completed rounds)
        completed_rounds = db.query(SessionRound).filter(
            SessionRound.session_id == session_id,
            SessionRound.status == "COMPLETED"
        ).all()

        if completed_rounds:
            total_score = sum(r.score for r in completed_rounds if r.score)
            session.overall_score = total_score / len(completed_rounds)

        # Mark session as COMPLETED if all rounds done
        if session.completed_rounds >= session.total_rounds:
            session.status = "COMPLETED"
            session.completed_at = datetime.utcnow()

        db.commit()
        db.refresh(session)
        db.refresh(round)

    except Exception as e:
        db.rollback()
        raise HTTPException(
            status_code=500,
            detail=f"Failed to save round data: {str(e)}"
        )

    # 4. Return completion response
    return {
        "session_id": session_id,
        "round_number": round_number,
        "status": "COMPLETED",
        "score": float(round.score) if round.score else 0.0,
        "completed_at": round.completed_at.isoformat(),
        "session_progress": {
            "completed_rounds": session.completed_rounds,
            "total_rounds": session.total_rounds,
            "overall_score": float(session.overall_score) if session.overall_score else 0.0,
            "session_status": session.status
        },
        "message": f"Round {round_number} completed! " +
                   (f"Check your email for Round {round_number + 1}." if session.completed_rounds < session.total_rounds
                    else "All rounds completed! Great job!")
    }


# ==================== SESSION SUMMARY ENDPOINT ====================

@router.get("/sessions/{session_id}")
async def get_session_summary(
    session_id: str,
    db: Session = Depends(get_db)
):
    """
    Get full session summary with all rounds.

    Returns:
    - Session metadata (user, resume, job, total rounds, overall score)
    - All rounds with their status, scores, and scheduled dates
    - Progress information (X of Y rounds completed)
    """
    # 1. Fetch session
    session = db.query(InterviewSession).filter(
        InterviewSession.id == session_id
    ).first()

    if not session:
        raise HTTPException(status_code=404, detail="Session not found")

    # 2. Fetch all rounds
    rounds = db.query(SessionRound).filter(
        SessionRound.session_id == session_id
    ).order_by(SessionRound.round_number).all()

    # 3. Build response
    return {
        "session_id": str(session.id),
        "user_id": session.user_id,
        "resume_id": session.resume_id,
        "job_listing_id": session.job_listing_id,
        "interview_type": session.interview_type,
        "difficulty_level": session.difficulty_level,
        "interrupt_level": session.interrupt_level,
        "status": session.status,
        "total_rounds": session.total_rounds,
        "completed_rounds": session.completed_rounds,
        "round_interval": session.round_interval,
        "overall_score": float(session.overall_score) if session.overall_score else None,
        "created_at": session.created_at.isoformat() if session.created_at else None,
        "started_at": session.started_at.isoformat() if session.started_at else None,
        "completed_at": session.completed_at.isoformat() if session.completed_at else None,
        "rounds": [
            {
                "round_number": r.round_number,
                "status": r.status,
                "scheduled_date": r.scheduled_date.isoformat() if r.scheduled_date else None,
                "scheduled_time": r.scheduled_time.isoformat() if r.scheduled_time else None,
                "email_sent_at": r.email_sent_at.isoformat() if r.email_sent_at else None,
                "started_at": r.started_at.isoformat() if r.started_at else None,
                "completed_at": r.completed_at.isoformat() if r.completed_at else None,
                "score": float(r.score) if r.score else None,
                "question_text": r.question_text,
                "answer_text": r.answer_text,
                "feedback_text": r.feedback_text,
                "question_audio_url": r.question_audio_url,
                "feedback_audio_url": r.feedback_audio_url
            }
            for r in rounds
        ]
    }
