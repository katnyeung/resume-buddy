"""Question generation and evaluation without LangGraph dependency."""
import json
from typing import Dict, Any
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
    llm = get_grok_llm(temperature=0.8)

    # Build context from job analysis (PRIORITY: rich data with skill assessments)
    resume_summary = ""
    job_analysis_data = state.get("job_analysis_data")
    print(f"[DEBUG] job_analysis_data is None: {job_analysis_data is None}")
    print(f"[DEBUG] job_analysis_data type: {type(job_analysis_data)}")

    if job_analysis_data:
        analysis = job_analysis_data
        if isinstance(analysis, dict):
            print(f"[DEBUG] job_analysis_data keys: {list(analysis.keys())}")
            print(f"[DEBUG] Has 'analysisResult'? {('analysisResult' in analysis)}")
            print(f"[DEBUG] Has 'analysis_result'? {('analysis_result' in analysis)}")
            print(f"[DEBUG] First 300 chars: {str(analysis)[:300]}")

        # The API returns a flat structure, not nested under 'analysisResult'
        # Use the data directly from the top level

        # Get job title and seniority
        job_title = analysis.get("normalizedTitle") or analysis.get("jobTitle", "Unknown Role")
        seniority = analysis.get("seniorityLevel", "")
        primary_soc = analysis.get("primarySocCode", "")

        resume_summary = f"**Candidate's Experience:**\n"
        if seniority:
            resume_summary += f"- **{job_title}** ({seniority} level)\n"
        else:
            resume_summary += f"- **{job_title}**\n"

        if primary_soc:
            resume_summary += f"- O*NET Code: {primary_soc}\n"

        # Add scores to show candidate's level
        impact_score = analysis.get("impactScore")
        tech_depth = analysis.get("technicalDepthScore")
        leadership_score = analysis.get("leadershipScore")

        if impact_score or tech_depth or leadership_score:
            resume_summary += f"- Scores: Impact={impact_score}/10, Technical Depth={tech_depth}/10, Leadership={leadership_score}/10\n"

        resume_summary += "\n"

        # Get extracted skills (technical skills)
        extracted_skills = analysis.get("extractedSkills", [])
        if extracted_skills:
            print(f"[DEBUG] First skill sample: {extracted_skills[0] if extracted_skills else 'EMPTY'}")
            resume_summary += "**Key Technical Skills:**\n"
            # Skills might be in different formats - try multiple field names
            for skill in extracted_skills[:10]:  # Top 10 skills
                if isinstance(skill, dict):
                    # Try different key combinations
                    name = (skill.get("skill") or skill.get("name") or
                           skill.get("skillName") or skill.get("technology") or "")
                    proficiency = (skill.get("proficiency") or skill.get("credibility") or
                                 skill.get("level") or skill.get("strength") or "")
                    if name:
                        if proficiency:
                            resume_summary += f"- {name} ({proficiency})\n"
                        else:
                            resume_summary += f"- {name}\n"
                elif isinstance(skill, str):
                    resume_summary += f"- {skill}\n"
            resume_summary += "\n"

        # Get key strengths
        key_strengths = analysis.get("keyStrengths", [])
        if key_strengths:
            resume_summary += f"**Key Strengths:** {', '.join(key_strengths[:5])}\n\n"

        # Get recruiter summary (comprehensive context)
        recruiter_summary = analysis.get("recruiterSummary", "")
        if recruiter_summary:
            resume_summary += f"**Experience Summary:** {recruiter_summary[:400]}...\n"

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
- TECHNICAL: Ask how their past experience relates to the job's technical requirements (system design, specific tech stack, scalability, testing)
- BEHAVIORAL: Ask for STAR examples that demonstrate skills needed for this job (teamwork, problem-solving, handling pressure)
- LEADERSHIP: Ask about leadership/mentoring experiences that align with the job's scope (if job needs team lead, ask about team building)

Difficulty Guidelines:
- JUNIOR: Focus on foundational skills, learning ability, and hands-on work
- MID: Focus on cross-functional collaboration, moderate system design, mentoring juniors
- SENIOR: Focus on architecture decisions, technical leadership, business impact
- STAFF: Focus on organizational strategy, cross-team influence, long-term vision

INSTRUCTIONS:
1. Review the TARGET JOB description first - what does THIS role actually need?
2. Look at the candidate's background - what have they proven they can do?
3. **Connect the dots**: Ask about their RELEVANT past experience that maps to what the job needs
4. Be specific: Reference the job's tech stack, challenges, or requirements
5. Ask concise questions (1-2 sentences max)
6. Avoid generic questions like "Tell me about yourself"

Examples of GOOD questions:
- "This role requires building microservices with Spring Boot on GCP. Can you walk me through how you achieved 99.95% uptime in your current system?"
- "Given your experience leading a distributed team across 3 time zones, how did you handle code review processes and maintain quality?"

Examples of BAD questions:
- "Tell me about your experience with Java" (too generic)
- "What's your biggest weakness?" (not tied to job needs)

Generate ONE interview question that connects the job's needs to the candidate's proven experience."""

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

    Returns:
        Dictionary with:
            - is_satisfactory
            - conversation_history (updated)
            - cumulative_scores (updated)
    """
    prompt = f"""You are an expert interview evaluator. Evaluate this candidate's answer.

Interview Type: {state['interview_type']}
Difficulty Level: {state['difficulty_level']}
Question: {state['question']}
Answer: {state['user_answer']}

Evaluation Criteria for {state['difficulty_level']} level:
- Clarity and structure (STAR format for behavioral)
- Specific examples with measurable outcomes
- Depth of technical/behavioral insight
- Communication skills
- Relevance to the question

Return a JSON object with:
{{
  "is_satisfactory": true or false,
  "score": 0.0-1.0 (numerical score),
  "reasoning": "Brief explanation of the score",
  "suggestions": ["suggestion1", "suggestion2"] (areas for improvement)
}}

IMPORTANT: Be realistic and fair. If the candidate:
- Provided a concrete example from their experience
- Explained their thought process
- Mentioned specific technologies or approaches
Then mark as "is_satisfactory": true even if not perfect.

Only mark false if the answer is completely off-topic or too vague (no examples at all)."""

    try:
        evaluation = await generate_json_completion(prompt, temperature=0.3)
    except Exception as e:
        print(f"[ERROR] Evaluation failed: {e}")
        # Fallback if JSON parsing fails
        evaluation = {
            "is_satisfactory": True,
            "score": 0.7,
            "reasoning": "Answer received and processed.",
            "suggestions": []
        }

    new_history = state.get("conversation_history", []) + [
        {"role": "user", "content": state["user_answer"], "type": "answer"},
        {
            "role": "system",
            "content": f"Score: {evaluation['score']:.2f} | {evaluation['reasoning']}",
            "type": "evaluation"
        }
    ]

    return {
        "is_satisfactory": evaluation["is_satisfactory"],
        "conversation_history": new_history,
        "cumulative_scores": state.get("cumulative_scores", []) + [evaluation["score"]]
    }
