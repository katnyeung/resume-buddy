"""WebSocket connection manager for active sessions."""
from typing import Dict
from fastapi import WebSocket


class ConnectionManager:
    """Manages active WebSocket connections for interview sessions."""

    def __init__(self):
        self.active_connections: Dict[str, WebSocket] = {}

    async def connect(self, session_id: str, websocket: WebSocket):
        """Accept WebSocket connection for a session."""
        await websocket.accept()
        self.active_connections[session_id] = websocket

    def disconnect(self, session_id: str):
        """Remove WebSocket connection."""
        if session_id in self.active_connections:
            del self.active_connections[session_id]

    async def send_json(self, session_id: str, data: dict):
        """Send JSON message to session."""
        if session_id in self.active_connections:
            await self.active_connections[session_id].send_json(data)

    async def send_bytes(self, session_id: str, data: bytes):
        """Send binary data to session."""
        if session_id in self.active_connections:
            await self.active_connections[session_id].send_bytes(data)

    def is_connected(self, session_id: str) -> bool:
        """Check if session has active connection."""
        return session_id in self.active_connections


# Global connection manager instance
manager = ConnectionManager()
