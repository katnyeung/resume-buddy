"""OpenAI client for STT (Whisper) and TTS."""
import os
import subprocess
from pathlib import Path
from openai import AsyncOpenAI


def get_client() -> AsyncOpenAI:
    """Get OpenAI client (lazy initialization)."""
    return AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY"))


async def transcribe_audio(audio_file_path: str) -> str:
    """
    Transcribe audio file to text using Whisper API.

    For WebM files (including fragmented streaming chunks), converts to WAV first
    using pydub + ffmpeg to ensure Whisper accepts the file.

    Args:
        audio_file_path: Path to audio file (supports mp3, mp4, mpeg, mpga, m4a, wav, webm)

    Returns:
        Transcribed text (empty string if file too small or conversion fails)
    """
    client = get_client()

    # Check file size first
    if os.path.getsize(audio_file_path) < 1000:  # Less than 1KB
        print(f"Audio file too small ({os.path.getsize(audio_file_path)} bytes), skipping")
        return ""

    # Determine file extension
    file_ext = Path(audio_file_path).suffix.lower()

    # Send WebM directly to Whisper (no conversion needed!)
    # Whisper API supports WebM format natively
    # Benefits: Smaller file size (compressed), faster upload, no ffmpeg overhead
    try:
        with open(audio_file_path, "rb") as audio_file:
            # Read file content
            audio_content = audio_file.read()
            print(f"[Transcribe] Sending WebM directly to Whisper ({len(audio_content)} bytes)")

            # Create BytesIO object for Whisper API
            from io import BytesIO
            audio_bytes = BytesIO(audio_content)
            audio_bytes.name = "audio.webm"  # Whisper needs a filename with extension

            # Request transcription (no hallucination filtering needed - frontend controls timing)
            response = await client.audio.transcriptions.create(
                model="whisper-1",
                file=audio_bytes,
                language="en"
            )

            # Return transcription text directly
            return response.text if hasattr(response, 'text') else ""

    except Exception as e:
        print(f"Transcription error: {e}")
        return ""


async def text_to_speech(text: str, output_path: str, voice: str = "alloy") -> str:
    """
    Convert text to speech using OpenAI TTS API.

    Args:
        text: Text to convert
        output_path: Path to save audio file
        voice: Voice to use (alloy, echo, fable, onyx, nova, shimmer)

    Returns:
        Path to saved audio file
    """
    client = get_client()
    response = await client.audio.speech.create(
        model="tts-1",
        voice=voice,
        input=text
    )

    # Ensure directory exists
    Path(output_path).parent.mkdir(parents=True, exist_ok=True)

    # Stream to file
    await response.astream_to_file(output_path)

    return output_path
