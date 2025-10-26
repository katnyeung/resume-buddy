"""Voice Activity Detection using simple energy-based detection."""
import struct
from typing import Optional


class VoiceActivityDetector:
    """Detects when user stops speaking using energy-based detection (no C++ compiler needed)."""

    def __init__(self, silence_threshold_ms: int = 800, energy_threshold: float = 500.0):
        """
        Initialize VAD.

        Args:
            silence_threshold_ms: Duration of silence to consider speech ended (ms)
            energy_threshold: Audio energy threshold (higher = less sensitive)
        """
        self.silence_threshold_ms = silence_threshold_ms
        self.energy_threshold = energy_threshold
        self.silence_duration_ms = 0
        self.is_speaking = False

    def _calculate_energy(self, audio_frame: bytes) -> float:
        """
        Calculate audio energy (RMS - Root Mean Square).

        Args:
            audio_frame: Raw audio bytes

        Returns:
            Energy level
        """
        try:
            # Convert bytes to 16-bit integers
            # Assuming PCM 16-bit mono audio
            num_samples = len(audio_frame) // 2
            if num_samples == 0:
                return 0.0

            samples = struct.unpack(f'{num_samples}h', audio_frame[:num_samples * 2])

            # Calculate RMS (Root Mean Square)
            sum_squares = sum(s ** 2 for s in samples)
            rms = (sum_squares / num_samples) ** 0.5

            return rms

        except Exception as e:
            return 0.0

    def process_frame(self, audio_frame: bytes) -> bool:
        """
        Process audio frame and update speech state.

        Args:
            audio_frame: Audio data (any size)

        Returns:
            True if speech detected, False if silence
        """
        energy = self._calculate_energy(audio_frame)

        # Speech detected if energy above threshold
        is_speech = energy > self.energy_threshold

        if is_speech:
            self.is_speaking = True
            self.silence_duration_ms = 0
            return True
        elif self.is_speaking:
            # User was speaking, now silent
            self.silence_duration_ms += 30  # Assume 30ms frames
            return False
        else:
            # Still silent (never started speaking)
            return False

    def is_speech_ended(self) -> bool:
        """
        Check if user has stopped speaking.

        Returns:
            True if silence threshold exceeded after speech
        """
        if not self.is_speaking:
            return False

        if self.silence_duration_ms >= self.silence_threshold_ms:
            # Reset state
            self.is_speaking = False
            self.silence_duration_ms = 0
            return True

        return False

    def reset(self):
        """Reset VAD state."""
        self.is_speaking = False
        self.silence_duration_ms = 0
