"""
TTS service for generating and managing audio files
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

    async def generate_audio(
        self,
        text: str,
        emotion: str = "neutral",
        session_id: Optional[str] = None,
        round_number: Optional[int] = None,
        voice_type: str = "narrator"  # "narrator" or "cipher"
    ) -> str:
        """
        Generate TTS audio and return URL or base64

        Args:
            text: Text to convert to speech
            emotion: Emotion type
            session_id: Session ID for filename
            round_number: Round number for filename
            voice_type: "narrator" for story, "cipher" for AI mentor

        Returns:
            URL to audio file or base64 data URI
        """
        try:
            # Select voice based on type
            if voice_type == "cipher":
                voice_id = self.elevenlabs_client.voice_cipher
            else:
                voice_id = self.elevenlabs_client.voice_narrator

            audio_bytes = await self.elevenlabs_client.generate_audio(
                text=text,
                voice_id=voice_id,
                emotion=emotion
            )

            # Option 1: Save to file and return URL
            if session_id and round_number is not None:
                # Add timestamp to avoid file locking issues on page reload
                timestamp = int(time.time() * 1000)  # Milliseconds for uniqueness
                filename = f"{session_id}_round{round_number}_{timestamp}.mp3"
                # Use os.path.join for cross-platform compatibility
                filepath = os.path.join(self.audio_dir, filename)

                # Ensure directory exists with proper permissions
                os.makedirs(self.audio_dir, exist_ok=True)

                try:
                    with open(filepath, "wb") as f:
                        f.write(audio_bytes)
                    print(f"✅ Audio file saved: {filepath}")
                except Exception as write_error:
                    print(f"❌ Failed to write audio file: {filepath}")
                    print(f"   Error: {write_error}")
                    print(f"   Attempting base64 fallback...")
                    # Fallback to base64 if file write fails
                    audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
                    print(f"✅ Using base64 data URI (size: {len(audio_base64)} chars)")
                    return f"data:audio/mp3;base64,{audio_base64}"

                return f"/audio/{filename}"

            # Option 2: Return as base64 data URI (for immediate playback)
            audio_base64 = base64.b64encode(audio_bytes).decode('utf-8')
            return f"data:audio/mp3;base64,{audio_base64}"

        except Exception as e:
            print(f"❌ TTS generation error: {e}")
            import traceback
            traceback.print_exc()
            # Return None if TTS fails (game continues without audio)
            return None

    def _cleanup_old_files(self, max_age_hours: int = 24):
        """
        Delete audio files older than max_age_hours
        Runs on service initialization to free disk space
        """
        try:
            now = time.time()
            max_age_seconds = max_age_hours * 3600
            deleted_count = 0

            for filename in os.listdir(self.audio_dir):
                if not filename.endswith('.mp3'):
                    continue

                filepath = os.path.join(self.audio_dir, filename)
                try:
                    file_age = now - os.path.getmtime(filepath)
                    if file_age > max_age_seconds:
                        os.remove(filepath)
                        deleted_count += 1
                except Exception as e:
                    # Skip files that can't be deleted (locked, etc.)
                    pass

            if deleted_count > 0:
                print(f"🧹 Cleaned up {deleted_count} old audio files (>{max_age_hours}h)")

        except Exception as e:
            print(f"⚠️ Audio cleanup failed: {e}")


# Singleton instance
tts_service = TTSService()
