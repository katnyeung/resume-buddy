"""Audio buffer for streaming audio chunks."""
import io
from typing import List


class AudioBuffer:
    """Buffers incoming audio chunks for processing."""

    def __init__(self):
        self.chunks: List[bytes] = []
        self.total_duration_ms = 0

    def append(self, chunk: bytes):
        """Add audio chunk to buffer."""
        self.chunks.append(chunk)
        # Assume 30ms per chunk (standard WebRTC frame)
        self.total_duration_ms += 30

    def get_audio(self) -> bytes:
        """Get concatenated audio data."""
        return b''.join(self.chunks)

    def clear(self):
        """Clear buffer."""
        self.chunks = []
        self.total_duration_ms = 0

    def duration_seconds(self) -> float:
        """Get buffer duration in seconds."""
        return self.total_duration_ms / 1000.0

    def is_empty(self) -> bool:
        """Check if buffer is empty."""
        return len(self.chunks) == 0
