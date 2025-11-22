"""
Piston API client for sandboxed code execution
"""
import os
import httpx
from typing import List, Dict, Any
from dotenv import load_dotenv

load_dotenv()

PISTON_API_URL = os.getenv("PISTON_API_URL", "https://emkc.org/api/v2/piston")


class PistonClient:
    def __init__(self):
        self.base_url = PISTON_API_URL

    async def execute_python(
        self,
        code: str,
        test_cases: List[Dict[str, Any]],
        timeout: int = 10
    ) -> Dict[str, Any]:
        """
        Execute Python code with test cases

        Args:
            code: Python code to execute
            test_cases: List of {"input": [...], "expected": ...}
            timeout: Execution timeout in seconds

        Returns:
            {
                "passed": 3,
                "total": 4,
                "results": [
                    {"input": [2,7,9], "expected": 12, "output": 12, "passed": True},
                    ...
                ],
                "error": None or error message
            }
        """
        results = []
        passed_count = 0

        for test_case in test_cases:
            test_input = test_case["input"]
            expected = test_case["expected"]

            # Build test code
            test_code = f"""{code}

# Test execution
result = rob({test_input})
print(result)
"""

            try:
                async with httpx.AsyncClient(timeout=timeout) as client:
                    response = await client.post(
                        f"{self.base_url}/execute",
                        json={
                            "language": "python",
                            "version": "3.10",
                            "files": [{"content": test_code}],
                        },
                    )

                    response.raise_for_status()
                    execution_result = response.json()

                    # Parse output
                    output_str = execution_result.get("run", {}).get("output", "").strip()
                    stderr = execution_result.get("run", {}).get("stderr", "")

                    if stderr:
                        results.append({
                            "input": test_input,
                            "expected": expected,
                            "output": None,
                            "passed": False,
                            "error": stderr[:200]
                        })
                    else:
                        try:
                            output = int(output_str) if output_str else None
                            passed = output == expected
                            if passed:
                                passed_count += 1

                            results.append({
                                "input": test_input,
                                "expected": expected,
                                "output": output,
                                "passed": passed,
                                "error": None
                            })
                        except ValueError:
                            results.append({
                                "input": test_input,
                                "expected": expected,
                                "output": output_str,
                                "passed": False,
                                "error": f"Invalid output: {output_str}"
                            })

            except Exception as e:
                results.append({
                    "input": test_input,
                    "expected": expected,
                    "output": None,
                    "passed": False,
                    "error": str(e)[:200]
                })

        return {
            "passed": passed_count,
            "total": len(test_cases),
            "results": results,
            "error": None
        }


# Singleton instance
piston_client = PistonClient()
