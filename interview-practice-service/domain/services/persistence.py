"""Persistence service for saving conversation data from Redis to PostgreSQL."""
from datetime import datetime
from sqlalchemy.orm import Session
from typing import Dict, Any

from domain.models import SessionRound, SessionRoundInteraction, InterviewSession
from infrastructure.clients.redis_client import RedisConversationStore


async def persist_round_to_database(
    session_id: str,
    round_num: int,
    db: Session,
    redis_store: RedisConversationStore
):
    """
    Save conversation data from Redis to PostgreSQL when round completes.

    This function:
    1. Reads conversation state from Redis
    2. Saves each interaction to interview_session_round_interactions table
    3. Updates interview_session_round with summary data
    4. Keeps Redis data for 24h (allows frontend to view after completion)

    Args:
        session_id: UUID of the interview session
        round_num: Round number (1, 2, 3...)
        db: SQLAlchemy database session
        redis_store: Redis conversation store instance
    """
    # Get conversation state from Redis
    state = await redis_store.get_state(session_id, round_num)
    if not state:
        print(f"[Persistence] WARNING: No Redis state found for {session_id} round {round_num}")
        return

    print(f"[Persistence] Persisting {len(state['interactions'])} interactions to database")

    try:
        # 1. Save each interaction to database
        for interaction in state["interactions"]:
            # Check if interaction already exists (avoid duplicates)
            existing = db.query(SessionRoundInteraction).filter_by(
                session_id=session_id,
                round_number=round_num,
                interaction_number=interaction["interaction_num"]
            ).first()

            if existing:
                print(f"[Persistence] Interaction {interaction['interaction_num']} already exists, skipping")
                continue

            # Parse timestamps
            question_asked_at = datetime.fromisoformat(interaction["timestamp"])
            answer_completed_at = None
            if interaction.get("updated_at"):
                answer_completed_at = datetime.fromisoformat(interaction["updated_at"])

            # Create new interaction record
            db_interaction = SessionRoundInteraction(
                session_id=session_id,
                round_number=round_num,
                interaction_number=interaction["interaction_num"],
                question_text=interaction["question"],
                answer_transcript=interaction.get("answer_transcript"),
                answer_duration_seconds=interaction.get("answer_duration_seconds"),
                satisfaction_level=interaction.get("satisfaction_level"),
                needs_followup=interaction.get("needs_followup"),
                feedback_text=interaction.get("feedback"),
                followup_question=interaction.get("followup_question"),
                final_summary=interaction.get("final_summary"),
                question_asked_at=question_asked_at,
                answer_completed_at=answer_completed_at,
                interrupt_count=interaction.get("interrupt_count", 0)
            )
            db.add(db_interaction)
            print(f"[Persistence] Saved interaction {interaction['interaction_num']}")

        # 2. Update interview_session_round with summary
        round_record = db.query(SessionRound).filter_by(
            session_id=session_id,
            round_number=round_num
        ).first()

        if not round_record:
            print(f"[Persistence] ERROR: Round record not found")
            return

        # First question
        round_record.question_text = state["interactions"][0]["question"]

        # Concatenate all answers
        all_answers = [
            i.get("answer_transcript", "")
            for i in state["interactions"]
            if i.get("answer_transcript")
        ]
        round_record.answer_text = "\n\n".join(all_answers)

        # Final score
        round_record.score = state.get("final_score")

        # Final feedback (from last interaction's final_summary)
        last_interaction = state["interactions"][-1] if state["interactions"] else {}
        feedback_text = last_interaction.get("final_summary") or last_interaction.get("feedback", "")
        round_record.feedback_text = feedback_text
        print(f"[Persistence] Saving feedback_text ({len(feedback_text) if feedback_text else 0} chars): {feedback_text[:100] if feedback_text else 'None'}...")

        # Status
        round_record.status = "COMPLETED"
        round_record.completed_at = datetime.utcnow()

        # Evaluation JSON (summary data)
        round_record.evaluation_json = {
            "total_interactions": len(state["interactions"]),
            "interrupt_count": len(state.get("interrupts", [])),
            "final_feedback": state["interactions"][-1].get("final_summary", ""),
            "conversation_flow": [
                {
                    "interaction": i["interaction_num"],
                    "satisfaction": i.get("satisfaction_level"),
                    "needs_followup": i.get("needs_followup")
                }
                for i in state["interactions"]
            ]
        }

        # Commit all changes
        db.commit()
        print(f"[Persistence] Successfully persisted round {round_num} to database")

        # 3. Update session overall_score (average of all completed rounds)
        await update_session_overall_score(session_id, db)

        # 4. Keep Redis data for 24h (don't delete - useful for frontend review)
        # Redis TTL will auto-expire after 24h

    except Exception as e:
        print(f"[Persistence] ERROR: {e}")
        db.rollback()
        raise


async def update_session_overall_score(session_id: str, db: Session):
    """
    Recalculate session overall_score as average of all completed rounds.

    Args:
        session_id: UUID of the interview session
        db: SQLAlchemy database session
    """
    session = db.query(InterviewSession).filter_by(id=session_id).first()
    if not session:
        return

    # Get all completed rounds with scores
    completed_rounds = db.query(SessionRound).filter_by(
        session_id=session_id,
        status="COMPLETED"
    ).all()

    if not completed_rounds:
        return

    # Calculate average score
    scores = [float(r.score) for r in completed_rounds if r.score is not None]
    if scores:
        session.overall_score = sum(scores) / len(scores)
        session.completed_rounds = len(completed_rounds)

        # Update session status if all rounds completed
        if len(completed_rounds) >= session.total_rounds:
            session.status = "COMPLETED"
            session.completed_at = datetime.utcnow()

        db.commit()
        print(f"[Persistence] Updated session overall_score: {session.overall_score:.2f}")
