"""Audio buffer for streaming audio chunks."""
import io
from typing import List, Optional


def find_cluster_offset(data: bytes) -> int:
    """
    Find first Cluster element (0x1F43B675) in WebM data.

    The Cluster element marks the start of actual audio data.
    Everything before it is the initialization segment (header).

    Returns:
        Byte offset of first Cluster, or len(data) if not found
    """
    cluster_marker = b'\x1F\x43\xB6\x75'
    offset = data.find(cluster_marker)
    return offset if offset != -1 else len(data)


class AudioBuffer:
    """Buffers incoming audio chunks for processing."""

    def __init__(self):
        self.chunks: List[bytes] = []
        self.total_duration_ms = 0
        # Separate accumulator for complete WebM file (keeps first chunk's header)
        self.accumulated_chunks: List[bytes] = []
        self.last_transcribed_chunk_count = 0  # Track what we've already transcribed
        # Store WebM header from first successful transcription
        self.stored_header: Optional[bytes] = None

    def append(self, chunk: bytes):
        """Add audio chunk to buffer."""
        self.chunks.append(chunk)
        self.accumulated_chunks.append(chunk)  # Also add to permanent accumulator
        # Assume 30ms per chunk (standard WebRTC frame)
        self.total_duration_ms += 30

    def get_audio(self) -> bytes:
        """Get concatenated audio data (current buffer window)."""
        return b''.join(self.chunks)

    def get_accumulated_audio(self) -> bytes:
        """Get ALL audio data since session start (includes WebM header from first chunk)."""
        return b''.join(self.accumulated_chunks)

    def get_accumulated_with_header(self) -> bytes:
        """
        Get accumulated audio with stored header prepended.

        If we have a stored header (from first successful transcription),
        prepend it to the accumulated chunks. Otherwise, return raw chunks
        (first transcription already has header).

        Returns:
            Complete WebM file with proper header
        """
        if self.stored_header:
            # Prepend stored header to accumulated chunks (which are raw Cluster data)
            return self.stored_header + b''.join(self.accumulated_chunks)
        else:
            # First transcription - chunks already include header
            return b''.join(self.accumulated_chunks)

    def store_header_from_file(self, webm_filepath: str):
        """
        Extract and store WebM header from a complete WebM file.

        Reads the file and extracts everything before the first Cluster element.
        This includes: EBML header + Segment + Info + Tracks

        Args:
            webm_filepath: Path to a complete WebM file (from first successful transcription)
        """
        try:
            with open(webm_filepath, 'rb') as f:
                data = f.read()

            cluster_offset = find_cluster_offset(data)
            if cluster_offset > 0:
                self.stored_header = data[:cluster_offset]
                print(f"[AudioBuffer] Stored WebM header ({len(self.stored_header)} bytes)")
            else:
                print(f"[AudioBuffer] Warning: No Cluster found in WebM file, not storing header")
        except Exception as e:
            print(f"[AudioBuffer] Failed to extract header: {e}")

    def clear(self):
        """Clear sliding window buffer (but keep accumulated chunks for complete file)."""
        self.chunks = []
        self.total_duration_ms = 0

    def reset_accumulator(self):
        """
        Reset accumulator (call when MediaRecorder restarts, e.g., after interrupt).

        Note: We keep stored_header across restarts because the MediaRecorder config
        (sample rate, codec) doesn't change within the same browser session.
        The old header works perfectly fine for new chunks from restarted MediaRecorder.
        """
        self.accumulated_chunks = []
        self.last_transcribed_chunk_count = 0
        # DON'T clear stored_header - reuse it for new MediaRecorder chunks
        print("[AudioBuffer] Accumulator reset (keeping stored header for reuse)")

    def reset_for_next_question(self):
        """
        Reset both buffers for next question in multi-turn conversation.

        Clears all audio data but keeps stored_header for reuse (MediaRecorder continues).
        Call this after sending a follow-up question to start collecting fresh answer.
        """
        self.chunks = []
        self.accumulated_chunks = []
        self.total_duration_ms = 0
        self.last_transcribed_chunk_count = 0
        # Keep stored_header - MediaRecorder doesn't restart, just collecting new answer
        print("[AudioBuffer] Reset for next question (keeping stored header)")

    def duration_seconds(self) -> float:
        """Get buffer duration in seconds."""
        return self.total_duration_ms / 1000.0

    def is_empty(self) -> bool:
        """Check if buffer is empty."""
        return len(self.chunks) == 0
