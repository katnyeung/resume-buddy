"""Interrupt Decision Agent - Decides when to interrupt user's answer."""
import json
from typing import Dict, Literal
from infrastructure.clients.grok_client import get_grok_llm


class InterruptAgent:
    """Analyzes user's answer in real-time and decides if AI should interrupt."""

    def __init__(self):
        self.llm = get_grok_llm(temperature=0.3)  # Lower temp for consistent decisions
        self.previous_interrupts = []  # Track what we've said before

    async def analyze(
        self,
        transcript: str,
        question: str,
        interview_type: str,
        difficulty_level: str,
        interrupt_level: Literal["CONSERVATIVE", "MODERATE", "AGGRESSIVE"],
        duration_seconds: float
    ) -> Dict:
        """
        Analyze ongoing transcript and decide if AI should interrupt.

        Args:
            transcript: User's answer so far
            question: Interview question being answered
            interview_type: TECHNICAL | BEHAVIORAL | LEADERSHIP
            difficulty_level: JUNIOR | MID | SENIOR | STAFF
            interrupt_level: User's preferred interrupt frequency
            duration_seconds: How long user has been speaking

        Returns:
            {
                "should_interrupt": bool,
                "reason": "rambling" | "unclear" | "missing_star" | "off_topic" | "none",
                "feedback_text": "Brief coaching tip (1-2 sentences)",
                "interrupt_type": "gentle" | "moderate" | "firm"
            }
        """
        # Build prompt based on interrupt level
        prompt = self._build_interrupt_prompt(
            transcript,
            question,
            interview_type,
            difficulty_level,
            interrupt_level,
            duration_seconds
        )

        try:
            # Call Grok LLM for decision
            response = await self.llm.ainvoke(prompt)
            result = json.loads(response.content)

            # If interrupting, save feedback to avoid repetition
            if result.get("should_interrupt", False):
                self.previous_interrupts.append({
                    "reason": result.get("reason", "none"),
                    "feedback": result.get("feedback", "")
                })

            return {
                "should_interrupt": result.get("should_interrupt", False),
                "reason": result.get("reason", "none"),
                "feedback_text": result.get("feedback", ""),
                "interrupt_type": result.get("interrupt_type", "gentle")
            }

        except Exception as e:
            # Fallback: Don't interrupt on error
            return {
                "should_interrupt": False,
                "reason": "error",
                "feedback_text": "",
                "interrupt_type": "gentle"
            }

    def _build_interrupt_prompt(
        self,
        transcript: str,
        question: str,
        interview_type: str,
        difficulty_level: str,
        interrupt_level: str,
        duration_seconds: float
    ) -> str:
        """Build LLM prompt for interrupt decision."""

        # Define interrupt thresholds based on level
        if interrupt_level == "CONSERVATIVE":
            rambling_threshold = 60  # 1 minute
            vagueness_tolerance = "high"
            star_strict = False
        elif interrupt_level == "MODERATE":
            rambling_threshold = 45  # 45 seconds
            vagueness_tolerance = "medium"
            star_strict = True if interview_type == "BEHAVIORAL" else False
        else:  # AGGRESSIVE
            rambling_threshold = 20  # 20 seconds
            vagueness_tolerance = "low"
            star_strict = True

        # Include previous interrupts in context
        previous_feedback_context = ""
        if self.previous_interrupts:
            previous_feedback_context = "\n**Previous Interrupts Given:**\n"
            for i, interrupt in enumerate(self.previous_interrupts, 1):
                previous_feedback_context += f"{i}. {interrupt['reason']}: {interrupt['feedback']}\n"
            previous_feedback_context += "\n⚠️ IMPORTANT: Do NOT repeat the same feedback! If user still hasn't addressed it after 2 interrupts, let them continue."

        prompt = f"""You are an expert interview coach analyzing a candidate's answer in REAL-TIME.

**Interview Context:**
- Type: {interview_type}
- Level: {difficulty_level}
- Interrupt Setting: {interrupt_level}
- Duration so far: {duration_seconds:.1f} seconds
{previous_feedback_context}

**Question Asked:**
"{question}"

**Candidate's Answer (so far):**
"{transcript}"

**Your Task:**
Decide if you should interrupt NOW with coaching feedback.

**Interrupt Guidelines ({interrupt_level}):**
1. **Rambling**: Interrupt if answer exceeds {rambling_threshold}s without clear progress
2. **Vagueness**: Tolerate {vagueness_tolerance} level of vague statements
3. **STAR Method** (for BEHAVIORAL): {'Strictly enforce' if star_strict else 'Gently guide'}
4. **Off-topic**: Interrupt if clearly not answering the question

**CONSERVATIVE Interrupts:**
- ONLY interrupt if:
  - Completely off-topic (>60s)
  - Missing STAR entirely in behavioral
  - Fundamental misunderstanding

**MODERATE Interrupts:**
- Interrupt if:
  - Rambling without progress (>45s)
  - Unclear/vague key points
  - Missing STAR components (behavioral)
  - Could benefit from redirection

**AGGRESSIVE Interrupts:**
- Interrupt if:
  - Any rambling (>20s)
  - Vague statements detected
  - Opportunity for better structure
  - Minor improvements possible

**Decision Criteria:**
✓ Is the answer progressing toward a clear point?
✓ Are they using specific examples (not vague)?
✓ For BEHAVIORAL: Do they have Situation, Task, Action, Result?
✓ Are they directly answering the question?

**Output Format (JSON only):**
```json
{{
  "should_interrupt": true/false,
  "reason": "rambling" | "unclear" | "missing_star" | "off_topic" | "none",
  "feedback": "Brief coaching tip (1-2 sentences, conversational tone)",
  "interrupt_type": "gentle" | "moderate" | "firm"
}}
```

**Examples:**

Example 1 (Rambling):
{{
  "should_interrupt": true,
  "reason": "rambling",
  "feedback": "Great start! Let's focus on the specific outcome - what was the measurable impact?",
  "interrupt_type": "gentle"
}}

Example 2 (Missing STAR):
{{
  "should_interrupt": true,
  "reason": "missing_star",
  "feedback": "I hear the situation. Now walk me through the specific actions YOU took.",
  "interrupt_type": "moderate"
}}

Example 3 (Good progress):
{{
  "should_interrupt": false,
  "reason": "none",
  "feedback": "",
  "interrupt_type": "gentle"
}}

Analyze and respond NOW:"""

        return prompt
