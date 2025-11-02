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

    # Get context flags (defaults to all enabled)
    context_flags = state.get("context_flags", {
        "use_job_data": True,
        "use_description_lines": True,
        "use_coach_questions": True,
        "use_stars_analysis": True
    })

    print(f"\n{'='*80}")
    print(f"[DEBUG] Context Flags Received:")
    print(f"{'='*80}")
    for key, value in context_flags.items():
        print(f"  {key}: {value}")
    print(f"{'='*80}\n")

    # Build context from job analysis (SIMPLIFIED - use description lines only)
    resume_summary = ""
    job_analysis_data = state.get("job_analysis_data")

    print(f"[DEBUG] job_analysis_data present: {job_analysis_data is not None}")
    if job_analysis_data:
        print(f"[DEBUG] job_analysis_data type: {type(job_analysis_data)}")
        if isinstance(job_analysis_data, dict):
            print(f"[DEBUG] job_analysis_data keys (first 10): {list(job_analysis_data.keys())[:10]}")
    else:
        print(f"[DEBUG] ⚠️ No job_analysis_data in state!")

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
        print(f"[DEBUG] Found {len(description_lines)} description lines in job_analysis_data")

        # FILTER OUT already-asked topics
        asked_topics = state.get("asked_topics", [])
        print(f"[DEBUG] Already asked topics: {asked_topics}")

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

                    # Extract potentialQuestions and starsAnalysis from recruiterInsights (nested!)
                    recruiter_insights = line.get("recruiterInsights", {})
                    if recruiter_insights:
                        pot_q = recruiter_insights.get("potentialQuestions", [])
                        if pot_q:
                            all_potential_questions.extend(pot_q)

                        stars = recruiter_insights.get("starsAnalysis", [])
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

            # Only include description lines if flag is enabled
            if context_flags.get("use_description_lines", True):
                print(f"[DEBUG] ✅ Adding {len(lines_text[:8])} description lines to resume_summary")
                resume_summary += "**What They Actually Did:**\n"
                for text in lines_text[:8]:  # Top 8 (now in random order, without already-asked topics)
                    resume_summary += f"• {text}\n"
                resume_summary += "\n"
            else:
                print(f"[DEBUG] ❌ SKIPPED description lines (flag disabled)")

        # Use extracted potential questions from descriptionLineMappings
        # Only include if flag is enabled
        if context_flags.get("use_coach_questions", True) and all_potential_questions:
            print(f"[DEBUG] ✅ Adding {len(all_potential_questions[:5])} coach questions to resume_summary")
            # RANDOMIZE: Shuffle potential questions
            random.shuffle(all_potential_questions)
            resume_summary += "**Interview Coach Suggestions (what to ask about):**\n"
            for q in all_potential_questions[:5]:  # Top 5 suggested areas to probe (random order)
                resume_summary += f"- {q}\n"
            resume_summary += "\n"
        else:
            if not context_flags.get("use_coach_questions", True):
                print(f"[DEBUG] ❌ SKIPPED coach questions (flag disabled)")
            else:
                print(f"[DEBUG] ⚠️ No coach questions available in data")

        # Use extracted STARS analysis from descriptionLineMappings
        # Only include if flag is enabled
        if context_flags.get("use_stars_analysis", True) and all_stars_analysis:
            print(f"[DEBUG] ✅ Adding {len(all_stars_analysis[:5])} STARS suggestions to resume_summary")
            # RANDOMIZE: Shuffle STARS suggestions
            random.shuffle(all_stars_analysis)
            resume_summary += "**Areas to Dig Deeper:**\n"
            for suggestion in all_stars_analysis[:5]:  # Top 5 improvement areas (random order)
                resume_summary += f"- {suggestion}\n"
        else:
            if not context_flags.get("use_stars_analysis", True):
                print(f"[DEBUG] ❌ SKIPPED STARS analysis (flag disabled)")
            else:
                print(f"[DEBUG] ⚠️ No STARS analysis available in data")

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

    # Build context from job listing (only if flag is enabled)
    job_summary = ""
    job_data = state.get("job_data")

    print(f"[DEBUG] job_data present: {job_data is not None}")
    print(f"[DEBUG] use_job_data flag: {context_flags.get('use_job_data', True)}")

    if context_flags.get("use_job_data", True) and job_data:
        job = job_data
        print(f"[DEBUG] ✅ Building job_summary from job_data")
        print(f"[DEBUG] job_data type: {type(job)}")
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
            print(f"[DEBUG] ✅ Added job description ({len(job_description)} chars, showing {len(desc_preview)} chars)")
        else:
            print(f"[DEBUG] ⚠️ No job description found in job_data")
    elif not context_flags.get("use_job_data", True):
        print(f"[DEBUG] ❌ SKIPPED job_data (flag disabled)")
    elif not job_data:
        print(f"[DEBUG] ⚠️ No job_data available in state")

    # Previous conversation context
    prev_questions = [
        msg["content"]
        for msg in state.get("conversation_history", [])
        if msg["role"] == "assistant" and msg.get("type") == "question"
    ]

    # Build context availability note
    context_note = "**Available Context:**\n"
    if context_flags.get("use_job_data", True):
        context_note += "- ✅ Target job description included\n"
    else:
        context_note += "- ❌ Target job description NOT included (user disabled)\n"

    if context_flags.get("use_description_lines", True):
        context_note += "- ✅ Candidate's accomplishments included\n"
    else:
        context_note += "- ❌ Candidate's accomplishments NOT included (user disabled)\n"

    if context_flags.get("use_coach_questions", True):
        context_note += "- ✅ Coach suggestions included\n"
    else:
        context_note += "- ❌ Coach suggestions NOT included (user disabled)\n"

    if context_flags.get("use_stars_analysis", True):
        context_note += "- ✅ STARS improvement areas included\n"
    else:
        context_note += "- ❌ STARS improvement areas NOT included (user disabled)\n"

    # Debug: Print what context we're sending to LLM
    print(f"\n{'='*80}")
    print(f"[DEBUG] Question Generation Context for Round {state['current_round']}")
    print(f"{'='*80}")
    print(f"CONTEXT FLAGS: {context_flags}")
    print(f"\nJOB SUMMARY ({len(job_summary)} chars):")
    if job_summary:
        print(f"{job_summary[:500]}...")
    else:
        print(f"[EMPTY - No job data or flag disabled]")

    print(f"\nRESUME SUMMARY ({len(resume_summary)} chars):")
    if resume_summary:
        print(f"{resume_summary[:500]}...")
    else:
        print(f"[EMPTY - No resume data or all resume flags disabled]")

    print(f"\n{'='*80}")
    print(f"[DEBUG] Final Context Summary:")
    print(f"  - Job context included: {len(job_summary) > 0}")
    print(f"  - Resume context included: {len(resume_summary) > 0}")
    print(f"  - Total context chars: {len(job_summary) + len(resume_summary)}")
    print(f"{'='*80}\n")

    prompt = f"""You are an expert technical interviewer. Based on the context provided below, ask ONE natural interview question.

{context_note}

**Context Provided:**

{job_summary}

{resume_summary}

**Interview Settings:**
- Type: {state['interview_type']}
- Level: {state['difficulty_level']}
- Round: {state['current_round']}/{state['max_rounds']}

**Question Guidelines:**
- Ask WHY or HOW questions that probe decision-making and technical depth
- If resume details are provided: Ask about their specific experience
- If ONLY job details are provided: Ask about their experience WITH those job requirements
- Max 15 words
- DO NOT reference things not mentioned in the context above (no "that project", "that feature" if nothing is listed)

Generate ONE question now:"""

    # Add exclusion note if topics already covered
    if state.get("asked_topics"):
        prompt += f"\n\n(Note: Already discussed {', '.join(state['asked_topics'])} in previous rounds - focus on something different)"

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

    # Build previous round performance context if available
    prev_round_context = ""
    if state.get("previous_round_performance"):
        prev = state["previous_round_performance"]
        prev_round_context = f"""
**Previous Round Performance (Round {prev['round_number']}):**
- Score: {prev['final_score']}/100
- Feedback: {prev['final_feedback']}

When providing your final_summary (needs_followup=false), include QUALITATIVE comparison:
- What did they do BETTER this round? (e.g., "Much better technical depth than Round {prev['round_number']}")
- What could still IMPROVE? (e.g., "Still missing trade-off discussions")
- What stayed CONSISTENT? (e.g., "Good use of metrics in both rounds")

Make it ENCOURAGING but honest. Focus on behavioral changes, not just numbers.
"""

    prompt = f"""You are an expert interview evaluator conducting a conversational interview.

**Context:**
- Interview Type: {state['interview_type']}
- Difficulty Level: {state['difficulty_level']}
- Round: {current_round} of {max_rounds}
- This is attempt #{attempt_number} for this round
{prev_round_context}
**Current Question:** {state['question']}
**Candidate's Answer:** {state['user_answer']}

**Previous Conversation (ALL attempts numbered for reference):**
{conversation_context if conversation_context else "[This is Attempt #1 - the first question.]"}

**Your Task:**
Look at ALL the [Attempt #N] answers above (not just the most recent one). Consider the CUMULATIVE information provided across all numbered attempts.

**What Makes a GOOD Answer?**
Based on real senior engineering interview feedback:
- ✅ Explains WHY decisions were made
- ✅ Technical reasons: performance, scalability, maintainability, cost, trade-offs
- ✅ Business reasons: stakeholder requirements, vendor relationships, team expertise, timeline constraints

**Interview Type Expectations**:
- **TECHNICAL**: Business context is NOTED, but follow-ups should probe TECHNICAL DEPTH
  - Example: "Leadership chose Redis" → Acceptable context, but ask: "How did YOU optimize Redis for your specific use case?"
  - Goal: Assess technical skills WITHIN those constraints
- **BEHAVIORAL**: Organizational and interpersonal factors are PRIMARY (accept them fully)
- **LEADERSHIP**: Decision-making process and stakeholder management are PRIMARY

**Red Flags**:
- ❌ Name-dropping tools without explaining implementation
- ❌ Vague statements without specifics
- ❌ Deflecting technical questions with ONLY business justification (in TECHNICAL rounds)

Decide whether to:
1. Ask ONE MORE follow-up (if attempt ≤2 AND critical details still missing - push for WHY/HOW)
2. Provide FINAL feedback (if attempt ≥3 OR you have enough info)

**Decision Rules - MAXIMUM 3 ATTEMPTS:**
- Attempt 1: If answer is very vague, ask follow-up
- Attempt 2: Consider ALL previous answers. If still missing key details, ONE more followup
- Attempt 3+: **STOP ASKING FOLLOW-UPS**. Give final evaluation based on everything they've said so far.

**CRITICAL RULE FOR ATTEMPT #{attempt_number}:**
{"YOU MUST PROVIDE FINAL FEEDBACK NOW. Set needs_followup=false. The candidate has already provided " + str(attempt_number) + " answers. Evaluate based on the cumulative information provided." if attempt_number >= 3 else "You may ask ONE more follow-up if critical details are missing, but be lenient - they've already tried " + str(attempt_number) + " time(s)."}

**Avoiding Repetitive Follow-ups:**
- DO NOT ask the same question with different wording (e.g., "Why X?" then "What made you choose X?" - that's the SAME question)
- If your previous followup didn't get the detail you wanted, either:
  - Ask about a DIFFERENT aspect (change direction) - e.g., from "why" to "how", from "choice" to "implementation"
  - OR provide final feedback (accept what they gave you)
- If the original question was vague (e.g., "that tool" when no tool was mentioned), acknowledge the confusion and move on

**Business Reasons Handling**:
- **TECHNICAL interviews**: Business context (stakeholder decisions, vendor choice) = acceptable BUT still probe technical implementation
  - Score 50-60 for business context alone, then ask: "How did YOU work with that technology?" or "What technical challenges did you solve?"
- **BEHAVIORAL/LEADERSHIP interviews**: Business context = good answer, accept and move on (score 70+)

Remember: Look at the ENTIRE conversation history above, not just the last answer!

**When asking follow-ups**: Include 2-3 specific aspects for the candidate to address (e.g., "Consider: X, Y, or Z" or "For example: A, B, or C"). This helps them understand what dimensions to focus on.

Return STRICT JSON:
{{
  "satisfaction_level": 0-100 (numerical score),
  "needs_followup": true or false,
  "feedback": "Brief immediate response (1-2 sentences)",
  "followup_question": "Next question with specific aspects to address (if needs_followup=true, max 25 words including examples)",
  "final_summary": "Comprehensive evaluation (if needs_followup=false, 2-3 sentences with score reasoning)"
}}

**Examples:**

Tool name-dropping (attempt 1) - ❌ BAD:
Answer: "I used Redis for caching and it worked well"
{{
  "satisfaction_level": 30,
  "needs_followup": true,
  "feedback": "You mentioned Redis, but I'd like to understand your decision-making.",
  "followup_question": "Why choose Redis? Consider: data structures, performance needs, or alternative options.",
  "final_summary": ""
}}

Business reason given in TECHNICAL interview (attempt 2) - ⚠️ PROBE DEEPER:
Answer: "Our leadership wanted us to use Redis because we already had an enterprise license and our team had Redis experience"
Interview Type: TECHNICAL
{{
  "satisfaction_level": 50,
  "needs_followup": true,
  "feedback": "I understand the business context. Now let's focus on the technical implementation.",
  "followup_question": "How did you optimize Redis for your use case? Consider: memory management, data structures, or persistence strategy.",
  "final_summary": ""
}}

Business reason given in BEHAVIORAL interview (attempt 2) - ✅ ACCEPT:
Answer: "Our leadership wanted us to use Redis because we already had an enterprise license and our team had Redis experience"
Interview Type: BEHAVIORAL
{{
  "satisfaction_level": 70,
  "needs_followup": false,
  "feedback": "Good explanation of how organizational factors influenced your team's decisions.",
  "followup_question": "",
  "final_summary": "Your score is 70 out of 100. You clearly explained the business context (existing license, team expertise) that drove the decision. This shows understanding that real-world engineering involves balancing technical and organizational constraints. You demonstrated good awareness of stakeholder management."
}}

Confusion due to vague question (attempt 2) - ✅ MOVE ON:
Answer: "I'm not sure what specific tool you're referring to, we used several GenAI services"
{{
  "satisfaction_level": 50,
  "needs_followup": false,
  "feedback": "Fair point - the question wasn't specific enough.",
  "followup_question": "",
  "final_summary": "Your score is 50 out of 100. You correctly identified that the question lacked context. In future interviews, you could proactively clarify: 'We used both OpenAI for embeddings and Claude for summarization - which one would you like me to focus on?' This shows communication skills and prevents confusion."
}}

Good answer with technical depth (attempt 3 - MUST END) - ✅ GOOD:
Answer: "I chose Redis sorted sets over hash maps because we needed range queries for leaderboard rankings. Memcached lacks data structures, and PostgreSQL would require full table scans for top-100 queries at our 100K RPS scale. Redis pipelining cut network round-trips by 80%."
{{
  "satisfaction_level": 85,
  "needs_followup": false,
  "feedback": "Excellent technical depth on trade-offs and implementation details.",
  "final_summary": "Your score is 85 out of 100. You clearly explained WHY you chose Redis (data structures + performance), compared alternatives (Memcached, PostgreSQL), and provided LOW-LEVEL details (sorted sets, pipelining, 80% improvement). This demonstrates deep technical understanding, not just tool familiarity. [IF ROUND 2+: Much better technical depth than last round - you compared multiple solutions and explained trade-offs. Keep providing specific metrics like this. You're consistently strong at quantifying impact.]"
}}

**NOTE:** If previous_round_performance exists, add 1-2 sentences of qualitative comparison to final_summary."""

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
