"""
Round-robin game engine for DUEL mode
Handles game logic, code combination, and round progression
"""
from typing import Dict, Any, Optional
from infrastructure.clients.piston_client import piston_client


class RoundRobinEngine:
    def __init__(self):
        self.piston_client = piston_client

    def combine_code(self, ai_code: str, user_code: str) -> str:
        """
        Combine AI skeleton and user code

        Strategy:
        - If user code includes function definition, use user code
        - Otherwise, replace TODO in AI skeleton with user code
        """
        user_code = user_code.strip()

        # If user wrote complete function, use it
        if "def " in user_code:
            return user_code

        # Otherwise, try to insert user code into AI skeleton's TODO
        if "# TODO" in ai_code:
            # Find indentation of TODO line
            lines = ai_code.split("\n")
            for i, line in enumerate(lines):
                if "# TODO" in line:
                    indent = len(line) - len(line.lstrip())
                    # Indent user code to match
                    indented_user_code = "\n".join(
                        " " * indent + uline if uline.strip() else ""
                        for uline in user_code.split("\n")
                    )
                    lines[i] = indented_user_code
                    break
            return "\n".join(lines)

        # Fallback: append user code after AI code
        return ai_code + "\n\n" + user_code

    async def execute_code(
        self,
        combined_code: str,
        test_cases: list
    ) -> Dict[str, Any]:
        """
        Execute code with test cases via Piston API

        Returns:
        {
            "passed": 3,
            "total": 4,
            "results": [...],
            "error": None or error message
        }
        """
        return await self.piston_client.execute_python(combined_code, test_cases)

    def should_continue(self, round_number: int, execution_result: Dict[str, Any], max_rounds: int = 5) -> bool:
        """
        Determine if game should continue

        Continue if:
        - Haven't reached max rounds AND
        - Haven't passed all test cases
        """
        if round_number >= max_rounds:
            return False

        passed = execution_result.get("passed", 0)
        total = execution_result.get("total", 0)

        # If all tests passed, stop (success!)
        if passed == total and total > 0:
            return False

        return True

    def calculate_final_score(self, execution_result: Dict[str, Any]) -> int:
        """
        Calculate final score as percentage
        """
        passed = execution_result.get("passed", 0)
        total = execution_result.get("total", 1)
        return int((passed / total) * 100) if total > 0 else 0


# Singleton instance
round_robin_engine = RoundRobinEngine()
