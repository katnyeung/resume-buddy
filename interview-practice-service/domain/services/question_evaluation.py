"""Question generation and evaluation without LangGraph dependency."""
import json
import random
from typing import Dict, Any, List
from infrastructure.clients.grok_client import get_grok_llm, generate_json_completion


async def generate_question(state: Dict[str, Any]) -> Dict[str, Any]:
    """Generate interview question based on context.

    Args:
        state: Dictionary containing:
            - session_id, user_id
            - resume_data, job_analysis_data, job_data
            - interview_type, difficulty_level
            - current_round, max_rounds
            - conversation_history

    Returns:
        Dictionary with "question" key
    """
    llm = get_grok_llm(temperature=0.9)  # Higher temperature for more varied questions

    # Build context from job analysis (SIMPLIFIED - use description lines only)
    resume_summary = ""
    job_analysis_data = state.get("job_analysis_data")

    if job_analysis_data:
        analysis = job_analysis_data

        # DEBUG: Check what fields we actually have and extract nested data
        if isinstance(analysis, dict):
            print(f"[DEBUG] job_analysis_data top-level keys: {list(analysis.keys())}")

            # Data is nested under 'analysis_result' or 'analysisResult' column
            for key in ['analysis_result', 'analysisResult', 'analysis', 'data', 'result']:
                if key in analysis:
                    print(f"[DEBUG] Found nested data under '{key}' key")
                    nested = analysis[key]
                    if isinstance(nested, dict):
                        print(f"[DEBUG] Nested keys: {list(nested.keys())[:10]}...")  # First 10 keys
                        if 'descriptionLines' in nested or 'normalizedTitle' in nested:
                            print(f"[DEBUG] ✅ Using nested data from '{key}'")
                            analysis = nested  # Use nested data
                            break
                    elif isinstance(nested, str):
                        # If it's a JSON string, parse it
                        try:
                            import json
                            nested = json.loads(nested)
                            print(f"[DEBUG] Parsed JSON string from '{key}'")
                            if isinstance(nested, dict):
                                analysis = nested
                                break
                        except:
                            pass

            print(f"[DEBUG] Final analysis keys: {list(analysis.keys())[:10] if isinstance(analysis, dict) else 'NOT A DICT'}...")
            print(f"[DEBUG] Has descriptionLines: {'descriptionLines' in analysis if isinstance(analysis, dict) else False}")

        # Get job title and seniority (basic header)
        job_title = analysis.get("normalizedTitle") or analysis.get("jobTitle", "Unknown Role")
        seniority = analysis.get("seniorityLevel", "")

        resume_summary = f"**Candidate's Experience: {job_title}**"
        if seniority:
            resume_summary += f" ({seniority} level)\n\n"
        else:
            resume_summary += "\n\n"

        # Get description lines (the actual resume text - what they wrote)
        # Field name is 'descriptionLineMappings' not 'descriptionLines'
        description_lines = analysis.get("descriptionLineMappings", [])

        # FILTER OUT already-asked topics
        asked_topics = state.get("asked_topics", [])

        # RANDOMIZE: Shuffle description lines so LLM doesn't always pick the first one
        if description_lines:
            # Convert to list of strings and extract potentialQuestions
            lines_text = []
            all_potential_questions = []
            all_stars_analysis = []

            for line in description_lines:
                if isinstance(line, dict):
                    text = line.get("text", "")
                    if text:
                        lines_text.append(text)

                    # Extract potentialQuestions from each line mapping
                    pot_q = line.get("potentialQuestions", [])
                    if pot_q:
                        all_potential_questions.extend(pot_q)

                    # Extract starsAnalysis from each line mapping
                    stars = line.get("starsAnalysis", [])
                    if stars:
                        all_stars_analysis.extend(stars)

                elif isinstance(line, str):
                    lines_text.append(line)

            # FILTER: Remove lines that contain already-asked topics
            if asked_topics:
                filtered_lines = []
                for line in lines_text:
                    line_lower = line.lower()
                    # Check if this line contains any already-asked topic
                    contains_asked_topic = any(topic.lower() in line_lower for topic in asked_topics)
                    if not contains_asked_topic:
                        filtered_lines.append(line)
                    else:
                        print(f"[DEBUG] Filtered out line containing {asked_topics}: {line[:60]}...")
                lines_text = filtered_lines if filtered_lines else lines_text  # Keep all if nothing left

            # Shuffle the order (different each time!)
            random.shuffle(lines_text)

            resume_summary += "**What They Actually Did:**\n"
            for text in lines_text[:8]:  # Top 8 (now in random order, without already-asked topics)
                resume_summary += f"• {text}\n"
            resume_summary += "\n"

        # Use extracted potential questions from descriptionLineMappings
        if all_potential_questions:
            # RANDOMIZE: Shuffle potential questions
            random.shuffle(all_potential_questions)
            resume_summary += "**Interview Coach Suggestions (what to ask about):**\n"
            for q in all_potential_questions[:5]:  # Top 5 suggested areas to probe (random order)
                resume_summary += f"- {q}\n"
            resume_summary += "\n"

        # Use extracted STARS analysis from descriptionLineMappings
        if all_stars_analysis:
            # RANDOMIZE: Shuffle STARS suggestions
            random.shuffle(all_stars_analysis)
            resume_summary += "**Areas to Dig Deeper:**\n"
            for suggestion in all_stars_analysis[:5]:  # Top 5 improvement areas (random order)
                resume_summary += f"- {suggestion}\n"

    # Fallback to raw resume if no job_analysis available
    elif state.get("resume_data"):
        resume = state["resume_data"]
        experiences = resume.get("structuredData", {}).get("experiences", [])
        skills = resume.get("structuredData", {}).get("skills", [])

        if experiences:
            resume_summary = "**Candidate's Experience:**\n"
            for exp in experiences[:3]:  # Top 3 most recent
                company = exp.get("company", "Unknown Company")
                title = exp.get("title", "Unknown Role")
                duration = exp.get("duration", "")
                responsibilities = exp.get("responsibilities", [])[:3]  # Top 3 responsibilities

                resume_summary += f"- **{title}** at {company} ({duration})\n"
                for resp in responsibilities:
                    resume_summary += f"  • {resp}\n"

            # Add skills
            if skills:
                skill_list = ", ".join([s.get("name", s) if isinstance(s, dict) else str(s) for s in skills[:10]])
                resume_summary += f"\n**Key Skills:** {skill_list}\n"

    # Build context from job listing
    job_summary = ""
    if state.get("job_data"):
        job = state["job_data"]
        print(f"[DEBUG] job_data keys: {list(job.keys()) if isinstance(job, dict) else 'NOT A DICT'}")

        # Try different field names
        job_title = job.get("title") or job.get("jobTitle") or job.get("position") or "AI/ML Engineer Role"
        job_company = job.get("company") or job.get("companyName") or job.get("employer") or "Technology Company"
        job_description = job.get("description") or job.get("jobDescription") or ""

        # Extract key requirements from description
        job_summary = f"**Target Job Posting:**\n{job_title} at {job_company}\n\n"
        if job_description:
            # Limit to 800 chars to fit in prompt
            desc_preview = job_description[:800]
            if len(job_description) > 800:
                desc_preview += "..."
            job_summary += f"**Job Description:**\n{desc_preview}\n"

    # Previous conversation context
    prev_questions = [
        msg["content"]
        for msg in state.get("conversation_history", [])
        if msg["role"] == "assistant" and msg.get("type") == "question"
    ]

    # Debug: Print what context we're sending to LLM
    print(f"\n{'='*80}")
    print(f"[DEBUG] Question Generation Context for Round {state['current_round']}")
    print(f"{'='*80}")
    print(f"JOB SUMMARY ({len(job_summary)} chars):\n{job_summary[:500]}...")
    print(f"\nRESUME SUMMARY ({len(resume_summary)} chars):\n{resume_summary[:500]}...")
    print(f"{'='*80}\n")

    prompt = f"""You are an expert interviewer for the job posting below. The candidate is practicing to land THIS specific role.

═══════════════════════════════════════════════════════════════
TARGET JOB (What they're applying for):
{job_summary}
═══════════════════════════════════════════════════════════════

CANDIDATE'S BACKGROUND (What they've proven they can do):
{resume_summary}

Interview Type: {state['interview_type']}
Difficulty Level: {state['difficulty_level']}
Round: {state['current_round']}/{state['max_rounds']}

Interview Type Guidelines:
- TECHNICAL: Ask WHY/HOW questions that probe DEEP understanding, not just tool familiarity
  * ❌ BAD: "What databases have you used?" (surface-level, just listing tools)
  * ✅ GOOD: "Why did you choose PostgreSQL over MongoDB for that use case?" (trade-offs, characteristics)
  * ❌ BAD: "Tell me about your monitoring setup" (vague, allows tool name-dropping)
  * ✅ GOOD: "How would you troubleshoot an out-of-memory error in a Java application?" (JVM-specific, technical depth)

- BEHAVIORAL: Ask for STAR examples that demonstrate skills needed for this job (teamwork, problem-solving, handling pressure)
- LEADERSHIP: Ask about leadership/mentoring experiences that align with the job's scope (if job needs team lead, ask about team building)

Difficulty Guidelines:
- JUNIOR: Focus on foundational skills, learning ability, and hands-on work
- MID: Focus on cross-functional collaboration, moderate system design, mentoring juniors
- SENIOR: Focus on architecture decisions, technical leadership, business impact
- STAFF: Focus on organizational strategy, cross-team influence, long-term vision

INSTRUCTIONS:

You are a recruiter interviewing a candidate for the TARGET JOB above.

Your goal: Assess if the candidate's experience matches what the job needs.

**CRITICAL: Pick a DIVERSE, INTERESTING topic from their background!**

The "What They Actually Did" list is **ALREADY RANDOMIZED** - pick the FIRST accomplishment in the list!

**DO NOT always ask about the same topics** (Spring Boot, 99.95% uptime, etc.).

**For Round {state['current_round']}**: Pick a DIFFERENT accomplishment than previous rounds. Focus on measuring IMPACT, not just listing tools.

**REAL INTERVIEW FEEDBACK - What Senior Engineers Want to Hear:**
Based on actual interview debriefs, candidates often miss the mark by:
- ❌ Listing tools instead of explaining TRADE-OFFS (e.g., "I used Redis" vs "I chose Redis over Memcached because...")
- ❌ Name-dropping cloud services instead of LOW-LEVEL technical details (e.g., "I used GCP monitoring" vs "I analyzed heap dumps and GC logs to find the memory leak")
- ❌ Answering a DIFFERENT question than asked (e.g., asked "Java design flaws?" → answered "How I'd design a language")

Your question MUST:
- Force the candidate to explain WHY they made a decision (architecture, database choice, tool selection)
- Force the candidate to explain HOW at a TECHNICAL level (not just tool names, but actual implementation details)
- Be SPECIFIC enough that vague answers won't satisfy

Ask ONE natural question that:
- Focuses on TRADE-OFFS, DECISIONS, or TROUBLESHOOTING (not just "what did you do")
- Requires technical depth to answer well (mentioning tools alone won't cut it)
- Max 15 words
- WHY or HOW format (e.g., "Why PostgreSQL over MongoDB?" or "How did you debug the memory leak?")

Generate ONE question now:"""

    # Add exclusion warning if we have asked topics
    if state.get("asked_topics"):
        prompt += f"\n\n⚠️ NOTE: The resume lines above have been FILTERED to remove topics you already asked about: {', '.join(state['asked_topics'])}. Ask about something NEW."

    try:
        response = await llm.ainvoke(prompt)
        question = response.content.strip()

        # Clean up markdown artifacts
        if question.startswith('"') and question.endswith('"'):
            question = question[1:-1]

        return {"question": question}

    except Exception as e:
        print(f"[ERROR] Question generation failed: {e}")
        # Fallback question
        return {"question": f"Can you tell me about your experience with the key skills required for this {state['interview_type'].lower()} role?"}


async def evaluate_answer(state: Dict[str, Any]) -> Dict[str, Any]:
    """Evaluate user's answer and determine if satisfactory.

    Args:
        state: Dictionary containing:
            - interview_type, difficulty_level
            - question, user_answer
            - conversation_history
            - cumulative_scores
            - current_round, max_rounds (for round tracking)

    Returns:
        Dictionary with:
            - satisfaction_level (0-100)
            - needs_followup (boolean)
            - feedback (immediate feedback)
            - followup_question (if needs_followup=true)
            - final_summary (if needs_followup=false)
            - conversation_history (updated)
            - cumulative_scores (updated)
    """
    # Build conversation context for Grok with attempt numbers
    conversation_context = ""
    answer_count = 0
    question_count = 0

    for msg in state.get("conversation_history", []):
        if msg.get("type") == "question":
            question_count += 1
            conversation_context += f"\n[Question #{question_count}] {msg.get('content', '')}"
        elif msg.get("type") == "answer":
            answer_count += 1
            conversation_context += f"\n[Attempt #{answer_count} - User's Answer] {msg.get('content', '')}"
        elif msg.get("type") == "feedback":
            # Feedback from follow-ups
            conversation_context += f"\n[Interviewer's Feedback] {msg.get('content', '')}"

    current_round = state.get("current_round", 1)
    max_rounds = state.get("max_rounds", 3)
    attempt_number = len([m for m in state.get("conversation_history", []) if m.get("type") == "answer"]) + 1

    # Add summary of attempts so far
    if answer_count > 0:
        conversation_context += f"\n\n[SUMMARY: Candidate has provided {answer_count} answer(s) so far across the conversation above.]"

    prompt = f"""You are an expert interview evaluator conducting a conversational interview.

**Context:**
- Interview Type: {state['interview_type']}
- Difficulty Level: {state['difficulty_level']}
- Round: {current_round} of {max_rounds}
- This is attempt #{attempt_number} for this round

**Current Question:** {state['question']}
**Candidate's Answer:** {state['user_answer']}

**Previous Conversation (ALL attempts numbered for reference):**
{conversation_context if conversation_context else "[This is Attempt #1 - the first question.]"}

**Your Task:**
Look at ALL the [Attempt #N] answers above (not just the most recent one). Consider the CUMULATIVE information provided across all numbered attempts.

**What Makes a GOOD Technical Answer?**
Based on real senior engineering interview feedback:
- ✅ Explains TRADE-OFFS and WHY decisions were made (not just "I used X")
- ✅ Provides LOW-LEVEL technical details (heap dumps, GC logs, JVM flags, not just "I used monitoring")
- ✅ Answers the ACTUAL question asked (not a tangent)
- ❌ Name-dropping tools without explaining why/how
- ❌ Vague statements like "optimized for scalability" without specifics
- ❌ Listing technologies instead of discussing characteristics/trade-offs

Decide whether to:
1. Ask ONE MORE follow-up (if attempt ≤2 AND critical details still missing - push for WHY/HOW)
2. Provide FINAL feedback (if attempt ≥3 OR you have enough info)

**Decision Rules - MAXIMUM 3 ATTEMPTS:**
- Attempt 1: If answer is very vague, ask follow-up
- Attempt 2: Consider ALL previous answers. If still missing key details, ONE more followup
- Attempt 3+: **STOP ASKING FOLLOW-UPS**. Give final evaluation based on everything they've said so far.

**CRITICAL RULE FOR ATTEMPT #{attempt_number}:**
{"YOU MUST PROVIDE FINAL FEEDBACK NOW. Set needs_followup=false. The candidate has already provided " + str(attempt_number) + " answers. Evaluate based on the cumulative information provided." if attempt_number >= 3 else "You may ask ONE more follow-up if critical details are missing, but be lenient - they've already tried " + str(attempt_number) + " time(s)."}

Remember: Look at the ENTIRE conversation history above, not just the last answer!

Return STRICT JSON:
{{
  "satisfaction_level": 0-100 (numerical score),
  "needs_followup": true or false,
  "feedback": "Brief immediate response (1-2 sentences)",
  "followup_question": "Next question to ask (if needs_followup=true, max 15 words, ONE specific thing to probe)",
  "final_summary": "Comprehensive evaluation (if needs_followup=false, 2-3 sentences with score reasoning)"
}}

**Examples:**

Tool name-dropping (attempt 1) - ❌ BAD:
Answer: "I used Redis for caching and it worked well"
{{
  "satisfaction_level": 30,
  "needs_followup": true,
  "feedback": "You mentioned Redis, but I'd like to understand your decision-making.",
  "followup_question": "Why did you choose Redis over other caching solutions?",
  "final_summary": ""
}}

Still surface-level (attempt 2) - ❌ BAD:
Answer: "Redis is fast and has good documentation"
{{
  "satisfaction_level": 35,
  "needs_followup": true,
  "feedback": "I need more technical depth on the trade-offs.",
  "followup_question": "What Redis data structures did you use and why?",
  "final_summary": ""
}}

Good answer with technical depth (attempt 3 - MUST END) - ✅ GOOD:
Answer: "I chose Redis sorted sets over hash maps because we needed range queries for leaderboard rankings. Memcached lacks data structures, and PostgreSQL would require full table scans for top-100 queries at our 100K RPS scale. Redis pipelining cut network round-trips by 80%."
{{
  "satisfaction_level": 85,
  "needs_followup": false,
  "feedback": "Excellent technical depth on trade-offs and implementation details.",
  "final_summary": "Your score is 85 out of 100. You clearly explained WHY you chose Redis (data structures + performance), compared alternatives (Memcached, PostgreSQL), and provided LOW-LEVEL details (sorted sets, pipelining, 80% improvement). This demonstrates deep technical understanding, not just tool familiarity."
}}"""

    # HARD CUTOFF: After 3 attempts, force final evaluation (prevent infinite loops)
    if attempt_number >= 4:
        print(f"[HARD CUTOFF] Attempt #{attempt_number} - forcing final evaluation")
        # Calculate average score from previous attempts
        avg_score = 50  # Default if no history
        if state.get("cumulative_scores"):
            avg_score = int(sum(state["cumulative_scores"]) * 100 / len(state["cumulative_scores"]))

        evaluation = {
            "satisfaction_level": avg_score,
            "needs_followup": False,
            "feedback": "Thank you for your responses.",
            "followup_question": "",
            "final_summary": f"Your score is {avg_score} out of 100. Based on our conversation, you demonstrated some understanding of the topic. For future interviews, aim to provide more specific examples and quantifiable results upfront to strengthen your answers."
        }
    else:
        try:
            evaluation = await generate_json_completion(prompt, temperature=0.7)
        except Exception as e:
            print(f"[ERROR] Evaluation failed: {e}")
            # Fallback if JSON parsing fails
            evaluation = {
                "satisfaction_level": 70,
                "needs_followup": False,
                "feedback": "Answer received and processed.",
                "followup_question": "",
                "final_summary": "Your answer demonstrated understanding of the topic. Continue to provide specific examples in future interviews."
            }

    # Update conversation history
    new_history = state.get("conversation_history", []) + [
        {"role": "user", "content": state["user_answer"], "type": "answer"},
        {
            "role": "system",
            "content": evaluation.get("feedback", ""),
            "type": "feedback"
        }
    ]

    # If followup needed, add the question to history
    if evaluation.get("needs_followup", False) and evaluation.get("followup_question"):
        new_history.append({
            "role": "assistant",
            "content": evaluation["followup_question"],
            "type": "question"
        })

    return {
        "satisfaction_level": evaluation.get("satisfaction_level", 0),
        "needs_followup": evaluation.get("needs_followup", False),
        "feedback": evaluation.get("feedback", ""),
        "followup_question": evaluation.get("followup_question", ""),
        "final_summary": evaluation.get("final_summary", ""),
        "conversation_history": new_history,
        "cumulative_scores": state.get("cumulative_scores", []) + [evaluation.get("satisfaction_level", 0) / 100.0]
    }
