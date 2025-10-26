"""OpenAI client for STT (Whisper) and TTS."""
import os
from pathlib import Path
from openai import AsyncOpenAI

try:
    from pydub import AudioSegment
    PYDUB_AVAILABLE = True
except ImportError:
    PYDUB_AVAILABLE = False


def get_client() -> AsyncOpenAI:
    """Get OpenAI client (lazy initialization)."""
    return AsyncOpenAI(api_key=os.getenv("OPENAI_API_KEY"))


async def transcribe_audio(audio_file_path: str, skip_validation: bool = False) -> str:
    """
    Transcribe audio file to text using Whisper API.

    Args:
        audio_file_path: Path to audio file (supports mp3, mp4, mpeg, mpga, m4a, wav, webm)
        skip_validation: If True, skip file validation and send directly to Whisper

    Returns:
        Transcribed text
    """
    client = get_client()

    # Determine file extension
    file_ext = Path(audio_file_path).suffix.lower()

    # Skip conversion for streaming chunks (they're often incomplete WebM fragments)
    if skip_validation or file_ext != '.webm':
        # Send directly to Whisper
        try:
            with open(audio_file_path, "rb") as audio_file:
                # Read file content
                audio_content = audio_file.read()

                # Skip if file is too small (likely corrupted)
                if len(audio_content) < 1000:  # Less than 1KB
                    print(f"Audio file too small ({len(audio_content)} bytes), skipping")
                    return ""

                # Create BytesIO object
                from io import BytesIO
                audio_bytes = BytesIO(audio_content)
                audio_bytes.name = f"audio{file_ext}"  # Whisper needs a filename

                transcript = await client.audio.transcriptions.create(
                    model="whisper-1",
                    file=audio_bytes,
                    language="en"
                )
            return transcript.text
        except Exception as e:
            print(f"Transcription error: {e}")
            return ""

    # For non-streaming files, try conversion
    converted_path = None
    if file_ext == '.webm' and PYDUB_AVAILABLE:
        try:
            audio = AudioSegment.from_file(audio_file_path, format="webm")
            converted_path = audio_file_path.replace('.webm', '.wav')
            audio.export(converted_path, format="wav")
            audio_file_path = converted_path
            file_ext = '.wav'
        except Exception as e:
            print(f"Audio conversion failed, using original: {e}")
            converted_path = None

    try:
        with open(audio_file_path, "rb") as audio_file:
            transcript = await client.audio.transcriptions.create(
                model="whisper-1",
                file=audio_file,
                language="en"
            )
        return transcript.text
    finally:
        # Clean up converted file
        if converted_path and os.path.exists(converted_path):
            os.remove(converted_path)


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
