"""
TTS service for generating story audio
Two voices: Chaos Engineer for chaos_story, System Architect for architect_story
"""
import os
import base64
import time
from typing import Optional
from infrastructure.clients.elevenlabs_client import elevenlabs_client


class TTSService:
    def __init__(self):
        self.elevenlabs_client = elevenlabs_client
        # Directory to store audio files
        self.audio_dir = "static/audio"
        os.makedirs(self.audio_dir, exist_ok=True)
        # Cleanup old files on startup
        self._cleanup_old_files()

    async def generate_story_audio(
        self,
        text: str,
        story_type: str = "chaos",  # "chaos" or "architect"
        session_id: Optional[str] = None,
        round_number: Optional[int] = None,
    ) -> Optional[str]:
        """
        Generate TTS audio for story narration

        Args:
            text: Story text to convert to speech
            story_type: "chaos" for chaos engineer, "architect" for system architect
            session_id: Session ID for filename
            round_number: Round number for filename

        Returns:
            URL to audio file or None if TTS fails
        """
        try:
            # Select voice and emotion based on story type
            if story_type == "chaos":
                voice_id = self.elevenlabs_client.voice_chaos
                emotion = "chaotic"
            else:
                voice_id = self.elevenlabs_client.voice_architect
                emotion = "calm"

            audio_bytes = await self.elevenlabs_client.generate_audio(
                text=text,
                voice_id=voice_id,
                emotion=emotion
            )

            # Save to file and return URL
            if session_id and round_number is not None:
                timestamp = int(time.time() * 1000)
                filename = f"{session_id}_round{round_number}_{story_type}_{timestamp}.mp3"
                filepath = os.path.join(self.audio_dir, filename)

                os.makedirs(self.audio_dir, exist_ok=True)

                try:
                    with open(filepath, "wb") as f:
                        f.write(audio_bytes)
                    print(f"   [TTS] Audio saved: {filename}")
                    return f"/audio/{filename}"
                except Exception as write_error:
                    print(f"   [TTS] Failed to write file: {write_error}")
                    # Fallback to base64
                    audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
                    return f"data:audio/mp3;base64,{audio_base64}"

            # Return as base64 if no session info
            audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
            return f"data:audio/mp3;base64,{audio_base64}"

        except Exception as e:
            print(f"   [TTS] Generation error: {e}")
            # Return None if TTS fails (game continues without audio)
            return None

    def _cleanup_old_files(self, max_age_hours: int = 24):
        """Delete audio files older than max_age_hours"""
        try:
            now = time.time()
            max_age_seconds = max_age_hours * 3600
            deleted_count = 0

            if not os.path.exists(self.audio_dir):
                return

            for filename in os.listdir(self.audio_dir):
                if not filename.endswith('.mp3'):
                    continue

                filepath = os.path.join(self.audio_dir, filename)
                try:
                    file_age = now - os.path.getmtime(filepath)
                    if file_age > max_age_seconds:
                        os.remove(filepath)
                        deleted_count += 1
                except Exception:
                    pass

            if deleted_count > 0:
                print(f"[TTS] Cleaned up {deleted_count} old audio files")

        except Exception as e:
            print(f"[TTS] Audio cleanup failed: {e}")


# Singleton instance
tts_service = TTSService()
