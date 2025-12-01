"""
X.AI Grok LLM client for chaos scenario generation and story responses
"""
import os
import json
import httpx
import time
from typing import Optional, Dict, Any, List
from dotenv import load_dotenv

load_dotenv()

XAI_API_KEY = os.getenv("XAI_API_KEY")
XAI_BASE_URL = os.getenv("GROK_BASE_URL", "https://api.x.ai/v1")
DEFAULT_MODEL = os.getenv("GROK_MODEL", "grok-beta")

# Enable verbose logging
GROK_VERBOSE = os.getenv("GROK_VERBOSE", "true").lower() == "true"


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
        max_tokens: int = 3000,
        retry_count: int = 0
    ) -> str:
        """
        Send chat completion request to Grok

        Args:
            prompt: User message
            system_prompt: System instructions
            response_format: {"type": "json_object"} for JSON output
            temperature: Randomness (0-1)
            max_tokens: Max response length
            retry_count: Internal retry counter

        Returns:
            Response text (or JSON string)
        """
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

            if response_format:
                payload["response_format"] = response_format

            # Verbose logging
            if GROK_VERBOSE:
                prompt_preview = prompt[:200] + "..." if len(prompt) > 200 else prompt
                system_preview = (system_prompt[:100] + "...") if system_prompt and len(system_prompt) > 100 else system_prompt
                print(f"\n   📤 [GROK REQUEST]")
                print(f"      Model: {self.model}")
                print(f"      Max tokens: {max_tokens}, Temp: {temperature}")
                print(f"      System: {system_preview}")
                print(f"      Prompt: {prompt_preview}")
                print(f"      Waiting for response...")

            try:
                request_start = time.time()
                response = await client.post(
                    f"{self.base_url}/chat/completions",
                    headers={
                        "Authorization": f"Bearer {self.api_key}",
                        "Content-Type": "application/json",
                    },
                    json=payload,
                )
                request_elapsed = time.time() - request_start

                response.raise_for_status()
                result = response.json()

                content = result["choices"][0]["message"]["content"]
                usage = result.get("usage", {})

                # Verbose response logging
                if GROK_VERBOSE:
                    print(f"   📥 [GROK RESPONSE] in {request_elapsed:.2f}s")
                    print(f"      Tokens: prompt={usage.get('prompt_tokens', '?')}, completion={usage.get('completion_tokens', '?')}, total={usage.get('total_tokens', '?')}")
                    content_preview = content[:150] + "..." if len(content) > 150 else content
                    print(f"      Response: {content_preview}")

                return content

            except httpx.ReadTimeout:
                if retry_count < 1:
                    print(f"⏱️ Grok API timeout, retrying...")
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
                    raise

            except Exception as e:
                print(f"❌ Grok API error: {e}")
                raise


# Singleton instance
grok_client = GrokClient()
