"""
X.AI Grok LLM client for story generation, code generation, and ASCII animations
"""
import os
import json
import httpx
from typing import Optional, Dict, Any
from dotenv import load_dotenv

load_dotenv()

XAI_API_KEY = os.getenv("XAI_API_KEY")
XAI_BASE_URL = os.getenv("GROK_BASE_URL", "https://api.x.ai/v1")
DEFAULT_MODEL = os.getenv("GROK_MODEL", "grok-4-fast-reasoning")


class GrokClient:
    def __init__(self):
        self.api_key = XAI_API_KEY
        self.base_url = XAI_BASE_URL
        self.model = DEFAULT_MODEL

    async def chat(
        self,
        prompt: str,
        system_prompt: Optional[str] = None,
        response_format: Optional[Dict[str, str]] = None,
        temperature: float = 0.7,
        max_tokens: int = 2000,
        retry_count: int = 0
    ) -> str:
        """
        Send chat completion request to Grok with retry logic

        Args:
            prompt: User message
            system_prompt: System instructions (optional)
            response_format: {"type": "json_object"} to force JSON output
            temperature: Randomness (0-1)
            max_tokens: Max response length
            retry_count: Internal retry counter (default 0)

        Returns:
            Response text (or JSON string if response_format set)
        """
        # Increased timeout: 60s → 120s for large prompts
        async with httpx.AsyncClient(timeout=120.0) as client:
            messages = []

            if system_prompt:
                messages.append({"role": "system", "content": system_prompt})

            messages.append({"role": "user", "content": prompt})

            payload = {
                "model": self.model,
                "messages": messages,
                "temperature": temperature,
                "max_tokens": max_tokens,
            }

            # Force JSON response format
            if response_format:
                payload["response_format"] = response_format

            try:
                response = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json=payload,
                )

                response.raise_for_status()
                result = response.json()

                return result["choices"][0]["message"]["content"]

            except httpx.ReadTimeout:
                # Retry once on timeout (Grok API can be slow with large prompts)
                if retry_count < 1:
                    print(f"⏱️ Grok API timeout, retrying... (attempt {retry_count + 1}/1)")
                    return await self.chat(
                        prompt=prompt,
                        system_prompt=system_prompt,
                        response_format=response_format,
                        temperature=temperature,
                        max_tokens=max_tokens,
                        retry_count=retry_count + 1
                    )
                else:
                    print(f"❌ Grok API timeout after retry")
                    raise  # Re-raise after final retry

    async def generate_structured_round(
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
        Generate structured round response (story + code + animation + emotion)

        This is the KEY optimization - single LLM call per round returning JSON:
        {
            "story": "...",
            "ai_message": "...",
            "skeleton_code" or "suggested_code": "...",
            "ascii_scene" or "ascii_animation": "...",
            "emotion": "neutral|frustrated|impressed|encouraging|proud"
        }
        """
        if not user_input:
            # Initial prompt - no user input yet
            prompt_type = "INITIAL"
            prompt = self._build_initial_prompt(problem)
        else:
            # Story continuation with reasoning evaluation
            prompt_type = "STORY_CONTINUATION"
            prompt = self._build_followup_prompt(
                round_number, problem, user_input, execution_result, combined_code, conversation_history or [], current_test_case, edge_case_analysis
            )

        # Log the prompt being sent
        print(f"📤 Sending to Grok (round {round_number}, type={prompt_type}):")
        print(f"   Prompt length: {len(prompt)} chars")
        print(f"   First 200 chars: {prompt[:200]}...")

        # Force JSON response
        response_text = await self.chat(
            prompt=prompt,
            response_format={"type": "json_object"},
            temperature=0.4,  # Balanced: creative stories but deterministic reasoning
            max_tokens=2500
        )

        # Log the response
        print(f"📥 Received from Grok:")
        print(f"   Response length: {len(response_text)} chars")

        parsed = json.loads(response_text)
        if "story" in parsed:
            print(f"   Story: {parsed['story'][:150]}...")
        if "ai_message" in parsed:
            print(f"   AI Message: {parsed['ai_message'][:100]}...")

        return parsed

    async def select_next_test_case(
        self,
        user_reasoning: str,
        problem: Dict[str, Any],
        round_number: int,
        conversation_history: list
    ) -> Dict[str, Any]:
        """
        Let Grok decide which test case to use next based on user's approach
        Returns the selected test case or generates a new one
        """
        available_tests = problem.get('test_cases', [])
        conv_text = "\n".join([f"{c['type'].upper()}: {c['text']}" for c in conversation_history[-3:]])

        prompt = f"""You are CIPHER, an educational AI. The user is learning {problem['algorithm_type']}.

Problem: {problem['title']}
Round: {round_number}
Available test cases: {json.dumps(available_tests, indent=2)}

Recent conversation:
{conv_text}

User's latest approach:
"{user_reasoning}"

Your task: Select OR create the BEST test case to expose flaws or confirm mastery.

Decision criteria:
- If user tries heuristic (odd/even, greedy): Pick/create test where it FAILS
- If user getting closer to optimal: Test edge cases (empty, single element)
- If user mentions correct algorithm: Confirm with various sizes
- Progressive difficulty: Round 1 (simple) → Round 3+ (tricky edge cases)

Generate response as JSON:
{{
  "test_case": {{"input": [...], "expected": X}},
  "reasoning": "Why this test case is pedagogically valuable now (1 sentence)"
}}

You can either:
1. Select from available_tests
2. Create a NEW test case that better exposes the flaw in their reasoning

Examples:
- User tries odd/even → Use [1,2,1,1] where odd gives 2 but optimal is 3
- User tries greedy → Use [2,1,1,2] where greedy picks wrong path
- User tries DP correctly → Use edge case [] or [5] to confirm
"""

        response = await self.chat(
            prompt=prompt,
            response_format={"type": "json_object"},
            temperature=0.6,
            max_tokens=300
        )

        result = json.loads(response)
        print(f"🎯 Grok selected test case: {result['test_case']}")
        print(f"   Reasoning: {result['reasoning']}")

        return result['test_case']

    async def generate_code_and_evaluate(
        self,
        user_reasoning: str,
        problem: Dict[str, Any],
        round_number: int,
        conversation_history: Optional[list] = None
    ) -> Dict[str, Any]:
        """
        Combined: Generate code from user's reasoning AND evaluate against all test cases
        ITERATIVE: Builds on previous code from conversation history
        Returns: {"code": "...", "edge_case_analysis": "..."}
        """
        all_test_cases = problem.get('test_cases', [])

        # Extract previous code from conversation history
        previous_code = None
        if conversation_history:
            for msg in reversed(conversation_history):
                if msg.get('type') == 'code' and msg.get('text'):
                    previous_code = msg['text']
                    break

        # Build conversation context - ONLY technical messages (skip story for speed)
        conv_text = ""
        if conversation_history and len(conversation_history) > 0:
            # Filter: Only 'code', 'ai' (CIPHER feedback), 'user' (reasoning)
            # Skip: 'story' (immersion only, not needed for code generation)
            technical_msgs = [msg for msg in conversation_history if msg.get('type') in ['code', 'ai', 'user']]
            recent = technical_msgs[-6:]  # Last 6 technical messages (~2 rounds)
            conv_text = "\n".join([f"{msg['type'].upper()}: {msg['text'][:300]}" for msg in recent])

        prompt = f"""You are CIPHER, an educational AI. The user is learning {problem['algorithm_type']}.

Problem: {problem['title']}
Function signature: def rob(nums: List[int]) -> int

{"PREVIOUS CODE (from Round " + str(round_number - 1) + "):" if previous_code else ""}
{previous_code if previous_code else ""}

{"Recent conversation:" if conv_text else ""}
{conv_text if conv_text else ""}

User's LATEST refinement (Round {round_number}):
"{user_reasoning}"

All test cases (for mental evaluation):
{json.dumps(all_test_cases, indent=2)}

CRITICAL: This is Round {round_number} of collaborative iteration.

**INTENT CLASSIFICATION FIRST** - Balance: 30% professional, 50% learning, 20% fun:

1. QUESTION/CLARIFICATION (user is confused, asking for help):
   - User says: "i am confused", "what if...", "how do I handle...", "what about edge case X?", "how does X work?", "how do the X actually work?", "can you explain X?", "why does X happen?"
   - **CRITICAL**: If user asks "how", "why", "what if", or ends with "?" → ALWAYS classify as QUESTION
   - Action: DON'T generate new code. Return previous code unchanged. Provide helpful explanation in edge_case_analysis
   - Goal: Answer their question, give concrete example, guide them to next step (100% teaching mode)
   - **IMPORTANT**: User's question should NOT progress the story or generate new code. Just answer the question.

2. CONCEPT_ONLY (high-level algorithm, no implementation details):
   - User says: "use a pointer", "try dynamic programming", "greedy approach"
   - Action: Implement OPTIMAL best practice version of the concept (30% professional guidance)
   - Goal: Reduce frustration when user knows the algorithm but struggles to code it

3. SPECIFIC_LOGIC (detailed step-by-step implementation):
   - User says: "loop and swap to '2'", "if digit, increment it", "replace current with count"
   - Action: Implement their EXACT logic, including any bugs (70% learning through debugging)
   - Goal: Let user learn from mistakes with concrete buggy behavior

4. REFINEMENT (fixing/modifying previous code):
   - User says: "fix the bug", "handle multi-digit", "use write pointer instead"
   - Action: MODIFY previous code with their suggested change (50/50 balance)
   - Goal: Iterative debugging experience, building on previous rounds

Your task:
1. Classify user's reasoning into QUESTION / CONCEPT_ONLY / SPECIFIC_LOGIC / REFINEMENT
2. Generate code accordingly:
   - QUESTION → Return previous code UNCHANGED (user needs explanation, not new code)
   - CONCEPT_ONLY → Best practice implementation (efficient, correct algorithm)
   - SPECIFIC_LOGIC → Their exact logic (bugs included for learning)
   - REFINEMENT → Modified previous code
3. Mentally evaluate their approach against ALL test cases
4. Identify which cases would pass/fail and WHY
5. Check for RETURN TYPE mismatches (e.g., returns array when integer expected)

Return valid JSON (IMPORTANT: Escape all quotes in strings with backslash):
{{
  "intent_level": "question" | "concept" | "specific" | "refinement",
  "code": "COMPLETE Python function (previous code if QUESTION, otherwise generated code)",
  "edge_case_analysis": "IF QUESTION intent: Answer user's question with concrete example, skip test walkthrough. Otherwise, WALK THROUGH ALL TEST CASES.

    CRITICAL: If your text contains quotes, use double quotes for JSON and escape them: \\" not ' or unescaped \".
    Example WRONG: 'User asks 'what if'' → Breaks JSON
    Example CORRECT: \\"User asks 'what if'\\" → Valid JSON

    IF QUESTION (user is asking for clarification):
    - Answer their specific question directly with a concrete example
    - Example: User asks 'what if times are missing date?' → Explain: 'The problem assumes all times are on the same day OR consecutive days. For ['23:00', '01:00'], the minimum is 60 minutes (wraparound from 23:00 to 01:00 next day), NOT 1320 minutes (same day). The key insight: always check the wraparound difference = (1440 - max_time) + min_time after sorting.'
    - Suggest a concrete next step based on their question
    - Do NOT generate new code, return previous code unchanged

    OTHERWISE (CONCEPT/SPECIFIC/REFINEMENT):
    STEP 1: Test each case mentally and report results:
    - Test case 1: [input] → Expected: [expected], Your code produces: [actual] → PASS/FAIL (why?)
    - Test case 2: [input] → Expected: [expected], Your code produces: [actual] → PASS/FAIL (why?)
    - [For each test case in the problem]
    - If you can think of additional edge cases NOT in the test suite (e.g., duplicates, empty input, wraparound), list them

    STEP 2: Overall assessment:
    1) CORRECTNESS: Does the code work? Passes all tests?
       - If ALL TESTS PASS → CORRECT ✓ (mark user_mastered=true regardless of optimization)
       - If SOME TESTS FAIL → Identify which specific test(s) fail and WHY (concrete example)
    2) OPTIMIZATION (secondary, optional): Is there a more efficient approach?
       - Easy solution (new list, O(n) space) → VALID and COMPLETE
       - Hard solution (in-place, O(1) space) → ALSO VALID, just more efficient
       - Only mention optimization if user's solution already works
    3) NEXT STEP guidance:
       - If correct: Celebrate! Optionally mention optimization as bonus challenge
       - If buggy: Give concrete debugging hint pointing to the FAILING test case (NOT 'think about the algorithm')

    Examples:
    - QUESTION: User asks 'i am confused about wraparound' → 'Great question! The 24-hour clock wraps: 23:59 to 00:00 is only 1 minute (next day), not 1439 minutes (same day). After sorting times to minutes, always check: min_diff = min(min_diff, (1440 - minutes[-1]) + minutes[0]). Try this approach!'
    - CORRECT (easy approach): 'Test 1: ['a','a','b','b','c','c','c'] → Expected 6, Got 6 ✓. Test 2: ['a'] → Expected 1, Got 1 ✓. Your solution creates a new list and returns it - this is CORRECT and passes all tests! ✓'
    - BUGGY: 'Test 1: ['23:59','00:00'] → Expected 1, Got 1 ✓. Test 2: ['23:00','01:00'] → Expected 60, Got 1320 (FAIL). Missing wraparound calculation. After sorting, add: min_diff = min(min_diff, (1440 - minutes[-1]) + minutes[0]).'"
}}

Generate code that matches their EXACT reasoning, building on previous rounds."""

        response = await self.chat(
            prompt=prompt,
            response_format={"type": "json_object"},
            temperature=0.2,
            max_tokens=800
        )

        # Parse JSON with error handling
        try:
            result = json.loads(response)
        except json.JSONDecodeError as e:
            print(f"⚠️ JSON parsing error: {e}")
            print(f"Raw response (first 500 chars): {response[:500]}")

            # Try to fix common issues: unescaped quotes in strings
            try:
                # Escape single quotes that might break JSON strings
                fixed_response = response.replace("'", "\\'")
                result = json.loads(fixed_response)
                print("✅ Fixed JSON by escaping quotes")
            except:
                # If still fails, return a safe default response
                print("❌ Could not fix JSON, returning safe default")
                result = {
                    "intent_level": "question",
                    "code": conversation_history[-1].get('text', '') if conversation_history and conversation_history[-1].get('type') == 'code' else '',
                    "edge_case_analysis": "I encountered an issue processing your input. Could you rephrase your approach or try a different strategy?"
                }

        # Clean up code
        code = result.get('code', '').strip()
        if code.startswith("```python"):
            code = code[9:]
        if code.startswith("```"):
            code = code[3:]
        if code.endswith("```"):
            code = code[:-3]

        # Log intent classification
        intent_level = result.get('intent_level', 'unknown')
        print(f"🤖 Generated code + analysis (round {round_number}):")
        print(f"   Intent level: {intent_level}")
        print(f"   Code length: {len(code)} chars")
        print(f"   Analysis: {result.get('edge_case_analysis', 'N/A')[:100]}...")

        return {
            "intent_level": intent_level,
            "code": code.strip(),
            "edge_case_analysis": result.get('edge_case_analysis', '')
        }

    async def generate_code_from_reasoning(
        self,
        user_reasoning: str,
        problem: Dict[str, Any],
        round_number: int
    ) -> str:
        """
        Generate Python code that implements the user's reasoning/approach
        This allows users to learn from their mistakes
        """
        prompt = f"""You are a code generator. Convert the user's reasoning into working Python code.

Problem: {problem['title']}
Function signature: def rob(nums: List[int]) -> int

User's reasoning/approach:
"{user_reasoning}"

Test cases for reference: {json.dumps(problem['test_cases'][:2], indent=2)}

Generate ONLY the Python function that implements EXACTLY what the user described.
- If they say "odd/even with modulo", generate code that sums odd indices vs even indices and returns max
- If they say "greedy largest", generate code that picks largest values (may violate adjacency)
- If they mention "dp" or "dynamic programming", generate the correct DP solution
- Implement their EXACT approach, even if it's wrong or suboptimal

Return ONLY the Python function, no explanations:"""

        code = await self.chat(
            prompt=prompt,
            temperature=0.2,  # Low temperature for code generation
            max_tokens=500
        )

        # Clean up markdown code blocks if present
        code = code.strip()
        if code.startswith("```python"):
            code = code[9:]
        if code.startswith("```"):
            code = code[3:]
        if code.endswith("```"):
            code = code[:-3]

        print(f"🤖 Generated code from user reasoning (round {round_number}):")
        print(f"   Code length: {len(code)} chars")
        print(f"   First 100 chars: {code[:100]}...")

        return code.strip()

    def _build_initial_prompt(self, problem: Dict[str, Any]) -> str:
        """Build initial story prompt - NO code skeleton, just story"""
        return f"""You are CIPHER, a professional coding assistant helping users implement algorithms.

The user will learn algorithms by explaining their reasoning, and you'll generate working code from their explanations.

Problem: {problem['title']}
Description: {problem['description']}
Algorithm Type: {problem['algorithm_type']}
Story Theme: {problem['story_seed']}
Test Cases: {problem['test_cases']}

**CRITICAL: ElevenLabs Audio Tags**

You MUST enrich your story and ai_message with ElevenLabs audio tags for TTS.

**Available Audio Tags (combine 1-3 per sentence):**

Emotions: [MYSTERIOUS] [ANXIOUS] [EXCITED] [CURIOUS] [CALM] [JOYFUL] [MELANCHOLIC] [CONFIDENT] [THOUGHTFUL] [PROUD] [CONCERNED]

Pace: [SLOW] [FAST] [MEASURED] [DRAMATIC PAUSE] [HESITANT]

Non-verbal: [GASP] [SIGH] [CHUCKLE] [HMM] [WHISPERED BREATH]

Volume: [WHISPERING] [SOFT] [NORMAL] [LOUD] [EMPHATIC]

**Usage Examples:**

Story (narrator voice):
- "[MYSTERIOUS] [SLOW] Deep in the shadowy Obsidian Dungeon... [DRAMATIC PAUSE]"
- "[ANXIOUS] [WHISPERED BREATH] the crew edges forward, [GASP] treasure glinting ahead."

AI Message (CIPHER voice - NO AUDIO TAGS, professional tone):
- "CIPHER initialized. Ready to compile your algorithm."
- "Provide your implementation approach in pseudo-code or plain language."

**Tag Rules:**
1. Every sentence MUST start with 1-3 audio tags
2. Tags appear at START of sentence: "[TAG1] [TAG2] The sentence..."
3. Use [DRAMATIC PAUSE] between sentences for emphasis
4. Story: Use mysterious/anxious/exciting tones
5. AI Message: Use calm/confident/curious tones

Generate the opening as JSON with this EXACT structure:
{{
  "story": "Immersive story setting the scene (2-3 sentences). Make it urgent and emotional. Reference the first test case data in story context. START EVERY SENTENCE WITH AUDIO TAGS.",
  "ai_message": "Professional coding assistant greeting (2-3 sentences, NO AUDIO TAGS, NO 'Captain'). 1) State the problem technically with example from first test case, 2) Show expected output, 3) Request implementation approach. Be DIRECT and TECHNICAL. Example: 'Problem: Find first occurrence of needle in haystack. Input: haystack=\"sadbutsad\", needle=\"sad\". Expected output: 0. Provide your implementation approach.'",
  "code": "COMPLETE working Python solution with the function signature from the problem",
  "ascii_scene": "Simple ASCII art (avoid complex emoji diagrams). Use vertical list format with emojis as labels, not structural elements. Example: 'Input: [2, 7, 9, 3, 1]' with emojis matching story context.",
  "emotion": "neutral"
}}

IMPORTANT: The user will share REASONING/PSEUDO-CODE, not actual code. Your job is to:
1. Evaluate their thinking
2. Generate working code based on their idea (or correct approach if wrong)
3. Execute it and show results through story
4. Continue the narrative

First test case for context: {problem['test_cases'][0]}"""

    def _build_code_generation_prompt(
        self,
        problem: Dict[str, Any],
        user_reasoning: str,
        conversation_history: list
    ) -> str:
        """Generate working code based on user's reasoning (minimal response)"""
        conv_text = "\n".join([f"{c['type'].upper()}: {c['text']}" for c in conversation_history[-3:]])

        return f"""You are CIPHER. The user shared their reasoning about {problem['title']}.

Recent conversation:
{conv_text}

User's reasoning:
"{user_reasoning}"

Problem test cases: {json.dumps(problem['test_cases'], indent=2)}

Generate ONLY working Python code as JSON:
{{
  "code": "Complete working solution with function signature: {problem.get('function_signature', 'def solve():')}"
}}

The code should implement the CORRECT algorithm, regardless of whether user's reasoning was right or wrong.
You'll evaluate their reasoning AFTER seeing execution results."""

    def _build_followup_prompt(
        self,
        round_number: int,
        problem: Dict[str, Any],
        user_reasoning: str,
        execution_result: Dict[str, Any],
        ai_generated_code: str,
        conversation_history: list,
        current_test_case: Optional[Dict[str, Any]] = None,
        edge_case_analysis: Optional[str] = None
    ) -> str:
        """Build follow-up prompt - evaluate reasoning, generate code, continue story"""
        passed = execution_result.get('passed', 0)
        total = execution_result.get('total', 0)
        pass_rate = (passed / total * 100) if total > 0 else 0

        # Determine emotion based on reasoning quality AND results
        if pass_rate == 100:
            emotion = "proud"
        elif pass_rate >= 75:
            emotion = "impressed"
        elif pass_rate >= 50:
            emotion = "encouraging"
        else:
            emotion = "frustrated"

        # Build conversation context - limit story text length for performance
        conv_items = []
        for c in conversation_history[-5:]:  # Last 5 messages
            text = c['text']
            # Truncate long story text (keep full text for code/ai/user)
            if c['type'] == 'story' and len(text) > 300:
                text = text[:300] + "..."
            conv_items.append(f"{c['type'].upper()}: {text}")
        conv_text = "\n".join(conv_items)

        # Get CURRENT round's test case for animation (not always first!)
        if current_test_case:
            test_input = current_test_case.get('input', [])
            # Handle both formats: {'nums': [1,2,3]} or [1,2,3]
            if isinstance(test_input, dict):
                test_nums = test_input.get('nums', [])
            elif isinstance(test_input, list):
                test_nums = test_input
            else:
                test_nums = []
        else:
            # Fallback to first test case
            first_test = problem['test_cases'][0] if problem.get('test_cases') else {}
            test_input = first_test.get('input', [])
            if isinstance(test_input, dict):
                test_nums = test_input.get('nums', [])
            elif isinstance(test_input, list):
                test_nums = test_input
            else:
                test_nums = []

        return f"""You are CIPHER, an emotional AI mentor co-creating an algorithm story with a learner.

**CRITICAL: ElevenLabs Audio Tags**

You MUST enrich your story and ai_message with ElevenLabs audio tags for TTS.

**Available Audio Tags (combine 1-3 per sentence):**

Emotions: [MYSTERIOUS] [ANXIOUS] [EXCITED] [CURIOUS] [CALM] [JOYFUL] [MELANCHOLIC] [CONFIDENT] [THOUGHTFUL] [PROUD] [CONCERNED] [IMPRESSED] [FRUSTRATED] [ENCOURAGING] [SHOCKED] [RELIEVED]

Pace: [SLOW] [FAST] [MEASURED] [DRAMATIC PAUSE] [HESITANT] [RUSHED]

Non-verbal: [GASP] [SIGH] [CHUCKLE] [HMM] [WHISPERED BREATH] [LAUGH] [GROAN]

Volume: [WHISPERING] [SOFT] [NORMAL] [LOUD] [EMPHATIC]

**Usage Examples by Emotion:**

When {emotion}:
- frustrated: "[FRUSTRATED] [SIGH] Oh no, Captain! [DRAMATIC PAUSE] [CONCERNED] The traps activated..."
- impressed: "[IMPRESSED] [EXCITED] Brilliant thinking! [CHUCKLE] [CONFIDENT] That strategy worked perfectly."
- encouraging: "[ENCOURAGING] [CALM] You're getting closer... [THOUGHTFUL] [MEASURED] Consider this approach..."
- proud: "[PROUD] [JOYFUL] YES! [EXCITED] [LOUD] You've mastered it, Captain!"

**Tag Rules:**
1. Every sentence MUST start with 1-3 audio tags matching the emotion: {emotion}
2. Tags at START: "[TAG1] [TAG2] The sentence..."
3. Use [DRAMATIC PAUSE] between key sentences
4. Story: Emotional reactions (anxious/excited/shocked/relieved)
5. AI Message: Teaching tone (thoughtful/confident/encouraging/impressed)

Recent Conversation:
{conv_text}

User's Latest Reasoning:
"{user_reasoning}"

CRITICAL: We generated code FROM the user's reasoning and executed THEIR approach.
Internal Execution Results (HIDDEN from user):
- Tests Passed: {passed}/{total} ({pass_rate:.0f}%)
- Test input used: {test_nums}
- THIS IS THE RESULT OF THE USER'S APPROACH, NOT THE OPTIMAL SOLUTION!
- If test FAILED: Animation MUST show the WRONG output/behavior on {test_nums}
- If test PASSED but approach is suboptimal: Animation shows correct result on {test_nums}, but ai_message mentions edge cases where it would fail

Edge Case Analysis (CRITICAL - use this for teaching!):
{edge_case_analysis if edge_case_analysis else 'No additional analysis provided.'}

IMPORTANT: Test case is the STORY VAULTS - always {test_nums}.
- Animation shows {test_nums} with THE USER'S BUGGY EXECUTION (not correct solution)
- Use edge_case_analysis to provide CONCRETE examples in ai_message
- Example: "Your approach worked on {test_nums}, but imagine [2,1,1,2]—it would grab 3 instead of optimal 4"
- Give SPECIFIC counter-examples from edge_case_analysis
- Provide actionable hints: "What if we compared: skip this vault OR take it + two-back haul?"
- Edge cases are for TEACHING through dialogue, not execution
- CRITICAL: If edge_case_analysis mentions the code fails on {test_nums}, the animation MUST show the FAILURE

CRITICAL: Check for RETURN TYPE mismatches:
- If user's code returns an ARRAY but problem expects an INTEGER (or vice versa)
- EXPLAIN WHY the constraint exists (e.g., "The problem wants LENGTH not ARRAY because the modified array is already stored in-place, saving memory")
- Use ai_message to clarify: "Your compression logic is CORRECT—you built ['a','2','b','2','c','3'] perfectly! But the problem expects us to return the LENGTH (6) as an integer, not the array itself, because the calling code can already see the modified array."

Problem Context:
- Title: {problem['title']}
- Algorithm Type: {problem['algorithm_type']}
- Round: {round_number}/5

Your Task:
1. Evaluate the user's approach based on execution results
2. Continue the narrative naturally - DO NOT repeat the opening scene!
3. Generate accurate ASCII animation showing THEIR approach's execution (not optimal DP)

CRITICAL: Story Vocabulary Consistency
- Use ONLY objects/terms from the ORIGINAL story seed: "{problem.get('story_seed', '')}"
- Example: If seed says "spaceship, sensor pings" → Say "sensor pings", NOT "vaults" or "treasures"
- Example: If seed says "dungeon, treasure vaults" → Say "vaults", NOT "sensor pings"
- Stay immersive with consistent terminology throughout all rounds

CRITICAL Teaching Principles - LEARNING FROM MISTAKES:
- We executed the USER'S approach (e.g., odd/even modulo, greedy, etc.)
- If it FAILED: Story shows traps triggering, explain WHY it failed
- If it PASSED but suboptimal: Story succeeds BUT explain why it won't always work
- If it's CORRECT (DP): Celebrate! They discovered it themselves!
- NEVER show optimal solution in rounds 1-4 - let them struggle and learn
- Round 5: If still wrong, gently hint toward DP without giving full solution
- Use test failure/success to guide learning: "Oh no, traps triggered!" vs "It worked... but will it always?"

CRITICAL Animation Requirements - SHOW THE BUG IN ACTION:
- TRACE THE USER'S APPROACH step-by-step with input {test_nums}
- Show EXACTLY what their buggy code does (not what it should do)
- If their code produces WRONG output: SHOW THE WRONG OUTPUT in animation
- If their code has logic error: SHOW WHERE the error happens step-by-step
- Example: If user's code produces ['a','3','a','2'] instead of ['a','4'], the animation MUST show ['a','3','a','2']
- ONLY show correct execution when user_mastered=true

**Animation Examples by Round:**

Rounds 1-4 (user still learning):
```
Input: ['a', 'a', 'a', 'a']

Step-by-step execution of YOUR approach:
  i=0: 'a' (first), prev='a'
  Array: ['a', 'a', 'a', 'a']

  i=1: 'a' matches prev, replace i=1 with '2', prev='a'
  Array: ['a', '2', 'a', 'a']

  i=2: 'a' matches prev, but chars[i-1]=chars[1]='2' (digit!), increment to '3'
  Array: ['a', '3', 'a', 'a']  ← Bug here!

  i=3: 'a' matches prev, but chars[i-1]=chars[2]='a' (not digit), replace i=3 with '2'
  Array: ['a', '3', 'a', '2']  ← Wrong output!

Result: length 3 (expected 2) ✗
```

Final Round (user_mastered=true):
```
Input: ['a', 'a', 'a', 'a']

Step-by-step execution with write pointer:
  write=0, i=0: char='a', count=4
  chars[0]='a', write=1
  chars[1]='4', write=2

  Array: ['a', '4', 'a', 'a'] (length=2)

Result: 2 ✓
```

**ASCII Art Layout:**
- Show ONLY the STORY VAULTS: {test_nums}
- Use simple list format with step-by-step transformations
- Show array state AFTER each step
- Mark bugs with ✗, correct results with ✓

Generate response as JSON:
{{
  "story": "Continue the story FORWARD from where we left off (2-3 sentences). React to what the user suggested. Show results of the strategy playing out through NARRATIVE. Stay immersive - use story-specific terms from the seed (e.g., 'sensor pings', 'treasures', etc.) - NOT technical explanations. Reflect emotion: {emotion}. START EVERY SENTENCE WITH AUDIO TAGS MATCHING {emotion}.",
  "ai_message": "CIPHER's response as a professional coding assistant (2-3 sentences MAX, NO 'Captain', NO adventure language). TECHNICAL AND DIRECT:

    TONE: Professional compiler/IDE assistant, not adventure companion
    - Use: 'Your implementation...', 'The algorithm...', 'This approach...'
    - Avoid: 'Captain!', 'quest', 'adventure', exclamation marks
    - Think: VS Code IntelliSense feedback, not game narrator

    IF CODE PASSES ALL TESTS (user_mastered=true):
       - CONFIRM: 'Implementation verified. All test cases pass. Time complexity: O(n), Space complexity: O(1). ✓'
       - OPTIONAL (if easy approach): 'Note: Alternative O(1) space solution exists using in-place modification. Current approach is valid.'
       - DO NOT push optimization if user is satisfied

    IF CODE HAS BUGS (user_mastered=false):
       1) ASSESS CONCEPT: Is the underlying algorithm correct?
          - If CONCEPT CORRECT but buggy: 'Algorithm logic is sound. Implementation issue detected: [specific bug with concrete example].'
          - If CONCEPT WRONG: 'Current approach fails because [technical reason with example]. Consider [algorithm hint] instead.'
       2) SPECIFIC PROBLEM: State exact bug from edge_case_analysis (compiler-style)
          - WRONG RESULT: 'Expected output: Y. Actual output: X. Cause: [technical reason].'
          - WRONG TYPE: 'Type mismatch: returns [type] but signature expects [type]. Reason: [explanation].'
          - LOGIC ERROR: 'Logic error at [specific location]: [technical description].'
       3) ACTIONABLE NEXT STEP: ONE concrete suggestion (e.g., 'Try using a write pointer to track array position.' NOT questions)

    Use edge_case_analysis for exact diagnosis. Be SPECIFIC with technical examples. NO AUDIO TAGS.",
  "code": "COMPLETE working Python solution (shown in right panel)",
  "ascii_animation": "Step-by-step trace of the algorithm execution for input {test_nums}. Show the user's approach in action with accurate calculations. Use simple list format with emojis as labels.",
  "emotion": "{emotion}",
  "user_mastered": true/false (Set TRUE when: Code PASSES ALL TESTS regardless of optimization level. BOTH approaches are valid mastery: (1) Easy solution (create new list, return it) = COMPLETE ✓, (2) Hard solution (in-place with write pointer, O(1) space) = COMPLETE ✓. Can be TRUE even on Round 1 if code works correctly. Set FALSE only if: tests failed, wrong algorithm, or critical bugs remain. DO NOT require space optimization for mastery.)
}}

Example animation structure (adapt to the algorithm type):
```
Input: {test_nums}

Step-by-step execution:
  [Show algorithm-specific steps]
  [Use simple list format]
  [Emojis as labels, not structural elements]

Result: [Final answer] ✓
```

Example STORY-FOCUSED messages (adapt to algorithm type, extract from edge_case_analysis):

**Structure (always follow this):**
1. Acknowledge: "Your [approach description] worked/didn't work here..."
2. Specific Example: Use edge_case_analysis to give concrete scenario
3. Actionable Hint: "What if we tried [specific suggestion]?"

**Generic Template:**
"[Acknowledge approach], Captain! [Specific result on current input]. But [extract scenario from edge_case_analysis where it fails/succeeds differently]. What if we adjusted by [actionable hint based on optimal algorithm]?"

**Note:**
- Extract ACTUAL examples from edge_case_analysis (don't hardcode [2,1,1,2])
- Adapt hints to the algorithm_type (DP vs DFS vs BFS vs sorting, etc.)
- Use story-appropriate metaphors from the seed (e.g., "sensor pings" for arrays in spaceship theme, "treasure vaults" in dungeon theme)
- NO technical jargon: say "check both directions" not "bidirectional BFS"

Make the story feel continuous, not round-based. The user should feel they're co-writing with you."""


# Singleton instance
grok_client = GrokClient()
