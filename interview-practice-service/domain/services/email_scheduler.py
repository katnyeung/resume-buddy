"""
Email scheduler for sending round reminder emails.

This module contains a cron job that runs daily at 9 AM to check for rounds
scheduled for today and sends reminder emails to users.
"""
from datetime import datetime, date, timedelta
from sqlalchemy.orm import Session
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from domain.models import InterviewSession, SessionRound
from infrastructure.database import SessionLocal
from infrastructure.clients import resume_api_client, jobsearch_api_client
import logging

logger = logging.getLogger(__name__)

# Create scheduler
scheduler = AsyncIOScheduler()


async def send_round_reminders():
    """
    Cron job that runs daily at 9 AM to send round reminder emails.

    Logic:
    1. Find all rounds scheduled for today (scheduled_date = today)
    2. Filter rounds that are PENDING and haven't been sent an email yet
    3. Check that the previous round (if any) is COMPLETED
    4. For each round:
       - Fetch session and job details
       - Send reminder email
       - Mark email_sent_at timestamp
    """
    logger.info("Running round reminder email job...")

    db: Session = SessionLocal()

    try:
        today = date.today()
        logger.info(f"Looking for rounds scheduled for {today}")

        # Query rounds scheduled for today
        rounds = db.query(SessionRound).filter(
            SessionRound.scheduled_date == today,
            SessionRound.status == "PENDING",
            SessionRound.email_sent_at.is_(None)  # Email not sent yet
        ).all()

        logger.info(f"Found {len(rounds)} rounds scheduled for today")

        rounds_to_remind = []
        for round in rounds:
            # Check if previous round is completed (if not round 1)
            if round.round_number > 1:
                previous_round = db.query(SessionRound).filter(
                    SessionRound.session_id == round.session_id,
                    SessionRound.round_number == round.round_number - 1
                ).first()

                if not previous_round or previous_round.status != "COMPLETED":
                    logger.info(f"Skipping round {round.round_number} for session {round.session_id}: previous round not completed")
                    continue

            rounds_to_remind.append(round)

        logger.info(f"Sending reminders for {len(rounds_to_remind)} rounds")

        # Send reminders
        for round in rounds_to_remind:
            try:
                await send_round_reminder_email(round, db)
            except Exception as e:
                logger.error(f"Failed to send reminder for round {round.id}: {e}")
                # Continue with other rounds

        db.commit()
        logger.info(f"Round reminder job complete. Sent {len(rounds_to_remind)} reminders.")

    except Exception as e:
        logger.error(f"Error in send_round_reminders: {e}")
        db.rollback()

    finally:
        db.close()


async def send_round_reminder_email(round: SessionRound, db: Session):
    """
    Send reminder email for a specific round.

    Args:
        round: SessionRound object
        db: Database session
    """
    # Fetch session
    session = db.query(InterviewSession).filter(
        InterviewSession.id == round.session_id
    ).first()

    if not session:
        logger.error(f"Session {round.session_id} not found for round {round.id}")
        return

    # Fetch user info
    try:
        user_info = await resume_api_client.get_user_info(session.user_id)
        user_name = user_info.get("fullName", "there")
    except Exception as e:
        logger.warning(f"Could not fetch user info for user {session.user_id}: {e}")
        user_name = "there"

    # Fetch job listing details (if applicable)
    job_title = "General Interview Practice"
    company_name = ""
    if session.job_listing_id:
        try:
            job_data = await jobsearch_api_client.get_job_listing(session.job_listing_id)
            if job_data:
                job_title = job_data.get("jobTitle", "General Interview Practice")
                company_name = job_data.get("companyName", "")
        except Exception as e:
            logger.warning(f"Could not fetch job listing {session.job_listing_id}: {e}")

    # Format scheduled datetime
    scheduled_datetime_str = f"{round.scheduled_date.strftime('%A, %B %d, %Y')} at {round.scheduled_time.strftime('%I:%M %p')} {session.timezone}"

    # Compose email
    email_subject = f"🎯 Interview Practice Round {round.round_number} Ready - {job_title}"

    # Build "what's next" message
    if round.round_number < session.total_rounds:
        whats_next = f"📬 What's Next?\nAfter completing this round, you'll receive an email for Round {round.round_number + 1} on the next scheduled date."
    else:
        whats_next = "🎉 This is your final round!\nAfter completing this round, you'll receive your overall performance summary."

    # Build company name part
    company_part = f" at {company_name}" if company_name else ""

    email_body = f"""
Hi {user_name},

Great progress! You're ready for Round {round.round_number} of your interview practice.

📅 Round Details:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Job Position: {job_title}{company_part}
• Round {round.round_number} of {session.total_rounds}
• Scheduled For: {scheduled_datetime_str}
• Coaching Level: {session.interrupt_level}

🎤 Start Round {round.round_number}:
Click the link below whenever you're ready:
https://resumebuddy.cv/interview/session/{session.id}/round/{round.round_number}

💡 Quick Tips:
• Find a quiet space
• Test your microphone
• Review feedback from previous rounds
• Take your time - there's no rush!

{whats_next}

Keep up the great work! 💪

Best regards,
Resume Buddy Team
    """

    # Send email via Resume API
    try:
        await resume_api_client.send_email(
            user_id=session.user_id,
            subject=email_subject,
            body=email_body
        )

        # Mark as sent
        round.email_sent_at = datetime.utcnow()
        db.add(round)  # Ensure round is tracked in current transaction

        logger.info(f"Sent reminder email for round {round.id} (Round {round.round_number}) to user {session.user_id}")

    except Exception as e:
        logger.error(f"Failed to send email for round {round.id}: {e}")
        raise


def start_scheduler():
    """
    Start the round reminder scheduler.

    Runs daily at 9:00 AM to check for rounds scheduled for today.
    """
    # Schedule job to run daily at 9 AM
    scheduler.add_job(
        send_round_reminders,
        'cron',
        hour=9,  # Run at 9 AM
        minute=0,
        id='round_reminder_job',
        replace_existing=True
    )

    scheduler.start()
    logger.info("Round reminder scheduler started. Will check for rounds daily at 9:00 AM.")


def stop_scheduler():
    """Stop the scheduler (for graceful shutdown)."""
    scheduler.shutdown()
    logger.info("Email reminder scheduler stopped.")
