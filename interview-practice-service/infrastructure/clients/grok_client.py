"""Grok LLM client via X.AI API."""
import os
import json
from typing import Dict, Any
from langchain_openai import ChatOpenAI


GROK_BASE_URL = os.getenv("GROK_BASE_URL", "https://api.x.ai/v1")
GROK_MODEL = os.getenv("GROK_MODEL", "grok-beta")
XAI_API_KEY = os.getenv("XAI_API_KEY")


def get_grok_llm(temperature: float = 0.7) -> ChatOpenAI:
    """
    Get Grok LLM client configured for X.AI.

    Args:
        temperature: Sampling temperature (0-1)

    Returns:
        ChatOpenAI instance configured for Grok
    """
    return ChatOpenAI(
        base_url=GROK_BASE_URL,
        api_key=XAI_API_KEY,
        model=GROK_MODEL,
        temperature=temperature
    )


async def generate_completion(
    prompt: str,
    temperature: float = 0.7,
    max_tokens: int = 2000
) -> str:
    """
    Generate completion from Grok LLM.

    Args:
        prompt: Input prompt
        temperature: Sampling temperature
        max_tokens: Maximum tokens to generate

    Returns:
        Generated text
    """
    llm = get_grok_llm(temperature=temperature)
    response = await llm.ainvoke(prompt)
    return response.content


async def generate_json_completion(
    prompt: str,
    temperature: float = 0.7
) -> Dict[str, Any]:
    """
    Generate JSON completion from Grok LLM.

    Args:
        prompt: Input prompt (should request JSON output)
        temperature: Sampling temperature

    Returns:
        Parsed JSON object
    """
    llm = get_grok_llm(temperature=temperature)
    response = await llm.ainvoke(prompt)

    # Extract JSON from response (handle markdown code blocks)
    content = response.content.strip()
    if content.startswith("```json"):
        content = content[7:]
    if content.startswith("```"):
        content = content[3:]
    if content.endswith("```"):
        content = content[:-3]

    return json.loads(content.strip())
