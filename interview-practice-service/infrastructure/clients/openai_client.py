"""OpenAI client for STT (Whisper) and TTS."""
import os
from pathlib import Path
from openai import AsyncOpenAI


def get_client() -> AsyncOpenAI:
    """Get OpenAI client (lazy initialization)."""
    return AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY"))


async def transcribe_audio(audio_file_path: str) -> str:
    """
    Transcribe audio file to text using Whisper API.

    Args:
        audio_file_path: Path to audio file (supports mp3, mp4, mpeg, mpga, m4a, wav, webm)

    Returns:
        Transcribed text
    """
    client = get_client()
    with open(audio_file_path, "rb") as audio_file:
        transcript = await client.audio.transcriptions.create(
            model="whisper-1",
            file=audio_file,
            language="en"
        )
    return transcript.text


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
