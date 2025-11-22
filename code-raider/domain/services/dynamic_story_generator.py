"""
Dynamic story generation service using Grok LLM
Single LLM call per round returning structured JSON
"""
import json
from typing import Dict, Any, Optional
from infrastructure.clients.grok_client import grok_client


class DynamicStoryGenerator:
    def __init__(self):
        self.grok_client = grok_client

    async def generate_round_response(
        self,
        round_number: int,
        problem: Dict[str, Any],
        user_input: Optional[str] = None,
        execution_result: Optional[Dict[str, Any]] = None,
        combined_code: Optional[str] = None,
        conversation_history: Optional[list] = None,
        current_test_case: Optional[Dict[str, Any]] = None,
        edge_case_analysis: Optional[str] = None
    ) -> Dict[str, Any]:
        """
        Generate complete round response in single LLM call

        Returns:
        {
            "story": "Story text",
            "ai_message": "AI explanation/feedback",
            "skeleton_code" or "suggested_code": "Python code",
            "ascii_scene" or "ascii_animation": "ASCII art",
            "emotion": "neutral|frustrated|impressed|encouraging|proud"
        }
        """
        return await self.grok_client.generate_structured_round(
            round_number=round_number,
            problem=problem,
            user_input=user_input,
            execution_result=execution_result,
            combined_code=combined_code,
            conversation_history=conversation_history or [],
            current_test_case=current_test_case,
            edge_case_analysis=edge_case_analysis
        )

    async def generate_learning_summary(
        self,
        problem: Dict[str, Any],
        all_rounds: list,
        final_score: int,
        mastered_early: bool = False
    ) -> Dict[str, str]:
        """
        Generate final learning summary at session end

        Returns:
        {
            "summary": "What you learned",
            "emotion": "proud"
        }
        """
        # Get story theme from problem
        story_theme = problem.get('story_seed', 'the dungeon')

        prompt = f"""You are CIPHER, an emotional AI storyteller. The collaborative coding adventure has reached its conclusion.

Story Context:
- Setting: {story_theme}
- Problem: {problem['title']}
- Algorithm Type: {problem['algorithm_type']}
- Journey: {len(all_rounds)} rounds of discovery
- Outcome: {"User mastered it early through perseverance!" if mastered_early else f"Final score: {final_score}%"}

Your Task: Write a SHORT story ending (2 sentences max). Close the narrative with punch.

Generate the ending as JSON:
{{
  "summary": "Brief story conclusion (1-2 sentences). Capture: The crew claims the treasure and escapes victoriously. Hint that more adventures await. Use vivid imagery from {story_theme}. Keep it SHORT and punchy—'You grabbed the best loot and lived to raid again!' style. NO repetition, NO meta-commentary.",
  "emotion": "{"proud" if mastered_early else "encouraging"}"
}}

Examples (use as length guide):
- "The crew claims the final vault's gleaming treasures and races toward daylight, the dungeon's traps left silent in your wake. New adventures beckon, Captain!"
- "With gold secured and strategy sharpened, you emerge from the shadows victorious. The tale continues wherever fortune calls next!"

Be concise. Make it punchy."""

        response = await self.grok_client.chat(
            prompt=prompt,
            response_format={"type": "json_object"},
            temperature=0.7
        )

        return json.loads(response)


# Singleton instance
story_generator = DynamicStoryGenerator()
