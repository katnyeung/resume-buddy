"""LangGraph state machine for interview practice sessions."""
import json
from typing import TypedDict, List, Literal
from langgraph.graph import StateGraph, START, END
from langgraph.checkpoint.redis.aio import AsyncRedisSaver
from infrastructure.clients.grok_client import get_grok_llm, generate_json_completion


class InterviewState(TypedDict):
    """LangGraph state (auto-persisted to Redis via RedisSaver)."""
    session_id: str
    user_id: int
    resume_data: dict
    job_analysis_data: dict  # Rich analysis with skill assessments, O*NET mappings
    job_data: dict
    interview_type: str  # TECHNICAL | BEHAVIORAL | LEADERSHIP
    difficulty_level: str  # JUNIOR | MID | SENIOR | STAFF
    current_round: int
    max_rounds: int
    question: str
    user_answer: str
    is_satisfactory: bool
    conversation_history: List[dict]
    cumulative_scores: List[float]


async def generate_greeting(state: InterviewState) -> dict:
    """Node: Generate welcome message."""
    interview_type = state["interview_type"].lower()
    difficulty = state["difficulty_level"].lower()

    greeting = (
        f"Hello! I'm excited to help you practice for your {interview_type} interview. "
        f"This session is tailored for a {difficulty}-level position. "
        f"I'll ask you {state['max_rounds']} questions, and we'll have a conversation about each one. "
        f"Feel free to take your time and provide detailed answers. Let's begin!"
    )

    return {
        "conversation_history": [{"role": "assistant", "content": greeting}]
    }


async def generate_question(state: InterviewState) -> dict:
    """Node: Generate interview question based on context."""
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
        for msg in state["conversation_history"]
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
- SENIOR: Focus on architecture, technical strategy, business impact, scaling teams
- STAFF: Focus on organizational vision, technical direction, cross-company influence

Previous questions asked: {prev_questions}

Generate ONE interview question that:
1. **PRIMARY FOCUS: What does the TARGET JOB need?** (e.g., if job needs React + TypeScript, ask about that)
2. **SECONDARY: Connect to their background** - Ask them to draw from their proven skills to demonstrate they can do the job
3. **SPECIFIC EXAMPLE**: Ask for a concrete story/project that shows they have the capability the job requires
4. **DIFFERENT TOPIC**: Must explore a NEW area from the job requirements (not previous questions)

Example (if job needs "scale microservices" and they have "Docker/K8s with STRONG credibility"):
❌ BAD: "Tell me about a time you worked on a machine learning project" (ignores job needs)
✅ GOOD: "This role requires scaling microservices to handle high traffic. Can you describe a time you used Docker or Kubernetes to improve system scalability? What challenges did you face and how did you solve them?"

CRITICAL:
- Focus on what THE JOB NEEDS first (job description requirements)
- Then ask candidate to prove they can do it with a specific example from their past
- DO NOT ask generic questions - reference actual job requirements
- DO NOT repeat topics from previous rounds

Return ONLY the question text, nothing else."""

    response = await llm.ainvoke(prompt)
    question = response.content.strip()

    new_history = state["conversation_history"] + [
        {"role": "assistant", "content": question, "type": "question"}
    ]

    return {
        "question": question,
        "conversation_history": new_history
    }


async def evaluate_answer(state: InterviewState) -> dict:
    """Node: Evaluate user's answer and determine if satisfactory."""
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
        # Fallback if JSON parsing fails
        evaluation = {
            "is_satisfactory": True,
            "score": 0.7,
            "reasoning": "Answer received and processed.",
            "suggestions": []
        }

    new_history = state["conversation_history"] + [
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


def should_continue(state: InterviewState) -> Literal["generate_question", "generate_feedback"]:
    """Edge: Decide next action based on rounds and satisfaction."""
    # If we've reached max rounds, generate feedback
    if state["current_round"] >= state["max_rounds"]:
        return "generate_feedback"

    # Otherwise, continue to next question
    return "generate_question"


async def generate_feedback(state: InterviewState) -> dict:
    """Node: Generate final feedback summary."""
    llm = get_grok_llm(temperature=0.7)

    avg_score = (
        sum(state["cumulative_scores"]) / len(state["cumulative_scores"])
        if state["cumulative_scores"]
        else 0.5
    )

    # Extract Q&A pairs
    qa_pairs = []
    current_q = None
    for msg in state["conversation_history"]:
        if msg.get("type") == "question":
            current_q = msg["content"]
        elif msg.get("type") == "answer" and current_q:
            qa_pairs.append(f"Q: {current_q}\nA: {msg['content']}")
            current_q = None

    conversation_summary = "\n\n".join(qa_pairs)

    prompt = f"""You are an expert interview coach. Provide constructive feedback for this interview practice session.

Interview Type: {state['interview_type']}
Difficulty Level: {state['difficulty_level']}
Average Score: {avg_score:.2f}/1.00

Conversation:
{conversation_summary}

Provide feedback with:
1. **Overall Assessment**: 2-3 sentences summarizing performance
2. **Strengths**: 3 specific things the candidate did well
3. **Areas for Improvement**: 3 specific, actionable areas to work on
4. **Next Steps**: 2-3 concrete action items for continued practice

Be encouraging but honest. Focus on specific examples from their answers."""

    response = await llm.ainvoke(prompt)
    feedback = response.content

    new_history = state["conversation_history"] + [
        {"role": "assistant", "content": feedback, "type": "feedback"}
    ]

    return {
        "conversation_history": new_history
    }


def build_interview_graph(checkpointer: AsyncRedisSaver) -> StateGraph:
    """
    Build LangGraph workflow with Redis checkpointer.

    Args:
        checkpointer: AsyncRedisSaver instance

    Returns:
        Compiled StateGraph
    """
    workflow = StateGraph(InterviewState)

    # Add nodes
    workflow.add_node("generate_greeting", generate_greeting)
    workflow.add_node("generate_question", generate_question)
    workflow.add_node("evaluate_answer", evaluate_answer)
    workflow.add_node("generate_feedback", generate_feedback)

    # Add edges
    workflow.add_edge(START, "generate_greeting")
    workflow.add_edge("generate_greeting", "generate_question")

    # Conditional edge after evaluation
    workflow.add_conditional_edges(
        "evaluate_answer",
        should_continue,
        {
            "generate_question": "generate_question",
            "generate_feedback": "generate_feedback"
        }
    )

    workflow.add_edge("generate_feedback", END)

    # Compile with Redis checkpointer
    return workflow.compile(checkpointer=checkpointer)
