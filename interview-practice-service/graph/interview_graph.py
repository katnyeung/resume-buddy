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

    # Build context from resume
    resume_summary = ""
    if state.get("resume_data"):
        resume_summary = f"Candidate's background: {json.dumps(state['resume_data'], indent=2)}"

    # Build context from job
    job_summary = ""
    if state.get("job_data"):
        job_summary = f"Target role: {json.dumps(state['job_data'], indent=2)}"

    # Previous conversation context
    prev_questions = [
        msg["content"]
        for msg in state["conversation_history"]
        if msg["role"] == "assistant" and msg.get("type") == "question"
    ]

    prompt = f"""You are an expert technical interviewer. Generate a {state['interview_type']} interview question for a {state['difficulty_level']}-level candidate.

{resume_summary}

{job_summary}

Interview Type Guidelines:
- TECHNICAL: System design, architecture, scalability, coding patterns, tech stack decisions
- BEHAVIORAL: STAR method (Situation, Task, Action, Result), teamwork, conflict resolution
- LEADERSHIP: Strategic thinking, mentorship, prioritization, cross-team collaboration

Difficulty Guidelines:
- JUNIOR: Individual contributions, basic concepts, learning experiences
- MID: System thinking, cross-functional work, moderate complexity
- SENIOR: Architecture, technical leadership, business impact, strategic decisions
- STAFF: Organizational strategy, long-term vision, company-wide influence

Previous questions asked: {prev_questions}

Round: {state['current_round']}/{state['max_rounds']}

Generate ONE specific question that:
1. References their actual experience from the resume
2. Is appropriate for the difficulty level
3. Encourages storytelling and specificity
4. **MUST be on a DIFFERENT topic** than previous questions - explore new areas!
5. Covers different aspects (e.g., if previous was scalability, ask about team collaboration, debugging, or architecture trade-offs)

IMPORTANT: DO NOT repeat similar themes. Move to a completely new topic each round.

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
