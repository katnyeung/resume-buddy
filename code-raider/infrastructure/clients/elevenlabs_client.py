"""
ElevenLabs TTS client for emotional voice narration
"""
import os
import httpx
from typing import Optional
from dotenv import load_dotenv

load_dotenv()

ELEVENLABS_API_KEY = os.getenv("ELEVENLABS_API_KEY")
ELEVENLABS_API_URL = "https://api.elevenlabs.io/v1"
# Two voices: Story narrator (background) and CIPHER (AI mentor)
VOICE_NARRATOR = os.getenv("ELEVENLABS_VOICE_NARRATOR", "RPEIZnKMqlQiZyZd1Dae")
VOICE_CIPHER = os.getenv("ELEVENLABS_VOICE_CIPHER", "RPEIZnKMqlQiZyZd1Dae")

# Model selection: eleven_turbo_v2_5 (free, no tags) OR eleven_v3 (paid, with tags)
TTS_MODEL = os.getenv("ELEVENLABS_MODEL", "eleven_turbo_v2_5")
STRIP_AUDIO_TAGS = os.getenv("ELEVENLABS_STRIP_TAGS", "true").lower() == "true"


class ElevenLabsClient:
    def __init__(self):
        self.api_key = ELEVENLABS_API_KEY
        self.base_url = ELEVENLABS_API_URL
        self.voice_narrator = VOICE_NARRATOR
        self.voice_cipher = VOICE_CIPHER
        self.model = TTS_MODEL
        self.strip_tags = STRIP_AUDIO_TAGS

    async def generate_audio(
        self,
        text: str,
        voice_id: Optional[str] = None,
        emotion: str = "neutral"
    ) -> bytes:
        """
        Generate TTS audio with emotional tone

        Args:
            text: Text to convert to speech (may contain audio tags like [GASP] [SIGH])
            voice_id: ElevenLabs voice ID (defaults to CIPHER's voice)
            emotion: Emotion type (neutral, frustrated, impressed, encouraging, proud)

        Returns:
            Audio bytes (MP3 format)
        """
        voice_id = voice_id or self.default_voice_id

        # Strip audio tags if using turbo model (doesn't support tags)
        if self.strip_tags:
            text = self._strip_audio_tags(text)

        # Map emotion to voice settings
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
                    "model_id": self.model,  # eleven_turbo_v2_5 or eleven_v3
                    "voice_settings": voice_settings,
                },
            )

            response.raise_for_status()
            return response.content

    def _strip_audio_tags(self, text: str) -> str:
        """
        Remove audio tags from text (e.g., [GASP], [SIGH], [DRAMATIC PAUSE])
        Only needed for eleven_turbo_v2_5 which doesn't support tags
        """
        import re
        # Remove all [TAG] patterns
        cleaned = re.sub(r'\[([A-Z\s]+)\]\s*', '', text)
        return cleaned.strip()

    def _get_voice_settings(self, emotion: str) -> dict:
        """
        Get voice settings based on emotion

        ElevenLabs voice settings (turbo_v2_5):
        - stability: 0-1 (0=more variable/emotional, 1=more stable/consistent)
        - similarity_boost: 0-1 (how closely to match the original voice)

        Note: 'style' parameter removed - not supported by turbo_v2_5 model
        """
        emotion_settings = {
            "neutral": {
                "stability": 0.5,
                "similarity_boost": 0.75,
            },
            "frustrated": {
                "stability": 0.3,  # More variable for emotion
                "similarity_boost": 0.7,
            },
            "impressed": {
                "stability": 0.4,
                "similarity_boost": 0.75,
            },
            "encouraging": {
                "stability": 0.6,  # More stable for supportive tone
                "similarity_boost": 0.8,
            },
            "proud": {
                "stability": 0.5,
                "similarity_boost": 0.8,
            },
        }

        return emotion_settings.get(emotion, emotion_settings["neutral"])

    async def get_voices(self) -> list:
        """
        Get list of available voices (for debugging)
        """
        async with httpx.AsyncClient() as client:
            response = await client.get(
                f"{self.base_url}/voices",
                headers={"xi-api-key": self.api_key},
            )
            response.raise_for_status()
            return response.json()


# Singleton instance
elevenlabs_client = ElevenLabsClient()
