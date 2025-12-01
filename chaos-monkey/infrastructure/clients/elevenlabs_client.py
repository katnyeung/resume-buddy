"""
ElevenLabs TTS client for chaos monkey story narration
Two voices: Chaos Engineer (chaos story) and System Architect (fix story)

FREE Pre-made voices (no character cost):
- 21m00Tcm4TlvDq8ikWAM - Rachel (calm, professional female)
- AZnzlk1XvdvUeBnXmlld - Domi (energetic female)
- ErXwobaYiN019PkySvjV - Antoni (calm male)
- TxGEqnHWrfWFTfGW9XjX - Josh (deep male)
- VR6AewLTigWG4xSOukaG - Arnold (narrative male)
- pNInz6obpgDQGcFmaJgB - Adam (deep male)
- yoZ06aMxZJJ28mfd3POQ - Sam (raspy male)
"""
import os
import httpx
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

ELEVENLABS_API_KEY = os.getenv("ELEVENLABS_API_KEY")
ELEVENLABS_API_URL = "https://api.elevenlabs.io/v1"

# FREE pre-made voices (don't count against character quota!)
# Chaos Engineer: Sam (raspy, energetic) - good for chaos narration
# System Architect: Antoni (calm male) - good for professional fixes
VOICE_CHAOS = os.getenv("ELEVENLABS_VOICE_CHAOS", "yoZ06aMxZJJ28mfd3POQ")  # Sam (free)
VOICE_ARCHITECT = os.getenv("ELEVENLABS_VOICE_ARCHITECT", "ErXwobaYiN019PkySvjV")  # Antoni (free)

# Model: eleven_turbo_v2_5 (fast, cheapest)
TTS_MODEL = os.getenv("ELEVENLABS_MODEL", "eleven_turbo_v2_5")


class ElevenLabsClient:
    def __init__(self):
        self.api_key = ELEVENLABS_API_KEY
        self.base_url = ELEVENLABS_API_URL
        self.voice_chaos = VOICE_CHAOS
        self.voice_architect = VOICE_ARCHITECT
        self.model = TTS_MODEL

    async def generate_audio(
        self,
        text: str,
        voice_id: Optional[str] = None,
        emotion: str = "neutral"
    ) -> bytes:
        """
        Generate TTS audio for story narration

        Args:
            text: Story text to convert to speech
            voice_id: ElevenLabs voice ID
            emotion: Emotion type (neutral, chaotic, calm, urgent)

        Returns:
            Audio bytes (MP3 format)
        """
        if not self.api_key:
            raise ValueError("ELEVENLABS_API_KEY not configured")

        voice_id = voice_id or self.voice_chaos

        # Get voice settings based on emotion
        voice_settings = self._get_voice_settings(emotion)

        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                f"{self.base_url}/text-to-speech/{voice_id}",
                headers={
                    "xi-api-key": self.api_key,
                    "Content-Type": "application/json",
                },
                json={
                    "text": text,
                    "model_id": self.model,
                    "voice_settings": voice_settings,
                },
            )

            response.raise_for_status()
            return response.content

    def _get_voice_settings(self, emotion: str) -> dict:
        """
        Get voice settings based on emotion

        ElevenLabs turbo_v2_5 settings:
        - stability: 0-1 (0=more variable/emotional, 1=more stable)
        - similarity_boost: 0-1 (how closely to match original voice)
        """
        emotion_settings = {
            "neutral": {
                "stability": 0.5,
                "similarity_boost": 0.75,
            },
            "chaotic": {
                "stability": 0.2,  # Very variable for chaos
                "similarity_boost": 0.7,
            },
            "calm": {
                "stability": 0.7,  # More stable for architect
                "similarity_boost": 0.8,
            },
            "urgent": {
                "stability": 0.3,
                "similarity_boost": 0.75,
            },
        }

        return emotion_settings.get(emotion, emotion_settings["neutral"])


# Singleton instance
elevenlabs_client = ElevenLabsClient()
