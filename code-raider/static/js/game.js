/**
 * Code Raider - Game Logic
 * WebSocket client + UI management
 */

console.log('🎮 Code Raider - Game Started');

const WS_URL = 'ws://localhost:8087/ws';
const token = localStorage.getItem('token');
let ws = null;
let currentRound = 1;
let isProcessing = false;

// Get session ID from URL
const urlParams = new URLSearchParams(window.location.search);
const sessionId = urlParams.get('session');

console.log('🔑 Session ID:', sessionId);
console.log('🔑 Token:', token ? 'Present' : 'Missing');

if (!sessionId) {
    console.error('❌ No session ID in URL');
    alert('No session ID found!');
    window.location.href = '/static/lobby.html';
}

// DOM elements
const chatMessages = document.getElementById('chatMessages');
const animationContent = document.getElementById('animationContent');
const codeDisplay = document.getElementById('codeDisplay');
const audioIndicator = document.getElementById('audioIndicator');
const audioPlayer = document.getElementById('audioPlayer');

let currentEditor = null;
let conversationHistory = []; // Track full story for context

// Audio queue for TTS playback (prevent overlap)
let audioQueue = [];
let isPlayingAudio = false;
let audioUnlocked = false;  // Track if user has interacted to unlock audio

// Connect to WebSocket
function connectWebSocket() {
    console.log('🔌 Attempting WebSocket connection...');
    console.log('URL:', `${WS_URL}/collab/${sessionId}`);

    ws = new WebSocket(`${WS_URL}/collab/${sessionId}`);

    ws.onopen = () => {
        console.log('✅ WebSocket CONNECTED');
        appendSystemMessage('✅ Connected to CIPHER. Initializing game...');
    };

    ws.onmessage = (event) => {
        console.log('📨 Received WebSocket message:', event.data);
        try {
            const data = JSON.parse(event.data);
            console.log('📦 Parsed data:', data);
            handleMessage(data);
        } catch (e) {
            console.error('❌ Failed to parse message:', e);
            appendSystemMessage('❌ Failed to parse server message');
        }
    };

    ws.onerror = (error) => {
        console.error('❌ WebSocket ERROR:', error);
        appendSystemMessage('❌ Connection error. Please refresh the page.');
    };

    ws.onclose = (event) => {
        console.log('🔌 WebSocket CLOSED:', event.code, event.reason);
        appendSystemMessage(`👋 Connection closed. Code: ${event.code}`);
    };
}

// Handle incoming messages
function handleMessage(data) {
    console.log('🎯 Handling message type:', data.type);

    switch (data.type) {
        case 'round_start':
            console.log('🎬 Round start data:', data);
            handleRoundStart(data);
            break;

        case 'round_result':
            console.log('📊 Round result data:', data);
            handleRoundResult(data);
            break;

        case 'game_complete':
            console.log('🏆 Game complete data:', data);
            handleGameComplete(data);
            break;

        case 'error':
            console.error('❌ Server error:', data.message);
            appendSystemMessage(`❌ Error: ${data.message}`);
            break;

        default:
            console.warn('⚠️ Unknown message type:', data.type);
    }
}

// Handle story start
function handleRoundStart(data) {
    console.log('🎬 Starting collaborative story...');
    console.log('📻 Audio URLs received:', {
        story: data.story_audio_url,
        cipher: data.cipher_audio_url,
        legacy: data.audio_url
    });

    // Display story with replay button (tags stripped in display)
    // WAIT for story to finish typing before showing CIPHER
    appendStoryMessage(data.story, data.story_audio_url, () => {
        // Callback: After story completes, show CIPHER message
        appendAIMessage(data.ai_message, data.cipher_audio_url);
        conversationHistory.push({type: 'ai', text: data.ai_message});

        // Create inline editor AFTER both messages finish typing
        createInlineEditor();
    });

    // Keep original text with tags in conversation history for context
    conversationHistory.push({type: 'story', text: data.story});

    // Show code in RIGHT panel (AI-generated reference code)
    if (data.code) {
        showCode(data.code);
        // Add to conversation history so Grok can iterate on it
        conversationHistory.push({type: 'code', text: data.code});
    }

    // Show animation in right panel
    if (data.animation) {
        showAnimation(data.animation);
    }

    // Queue TTS audio (story first, then CIPHER)
    if (data.story_audio_url) {
        console.log('🎵 Queueing STORY audio:', data.story_audio_url);
        queueAudio(data.story_audio_url);
    }
    if (data.cipher_audio_url) {
        console.log('🎵 Queueing CIPHER audio:', data.cipher_audio_url);
        queueAudio(data.cipher_audio_url);
    }
    // Backward compatibility
    if (data.audio_url) {
        console.log('🎵 Queueing LEGACY audio:', data.audio_url);
        queueAudio(data.audio_url);
    }

    console.log('🎵 Total queued:', audioQueue.length, 'files');
}

// Handle story continuation (after user input)
function handleRoundResult(data) {
    console.log('📖 Story continues...');

    // Display story reaction with replay button (tags stripped in display)
    // WAIT for story to finish typing before showing CIPHER
    appendStoryMessage(data.story, data.story_audio_url, () => {
        // Callback: After story completes, show CIPHER message
        appendAIMessage(data.ai_message, data.cipher_audio_url);
        conversationHistory.push({type: 'ai', text: data.ai_message});

        // Reset processing flag BEFORE creating new editor
        isProcessing = false;

        // Create new inline editor AFTER both messages finish typing
        createInlineEditor();
    });

    // Keep original text with tags in conversation history
    conversationHistory.push({type: 'story', text: data.story});

    // Show AI-generated working code in RIGHT panel
    if (data.suggested_code || data.code) {
        const generatedCode = data.suggested_code || data.code;
        showCode(generatedCode);
        // Add to conversation history so Grok can iterate on it
        conversationHistory.push({type: 'code', text: generatedCode});
    }

    // Show execution animation
    if (data.animation) {
        showAnimation(data.animation);
    }

    // Queue TTS audio (story first, then CIPHER)
    if (data.story_audio_url) {
        queueAudio(data.story_audio_url);
    }
    if (data.cipher_audio_url) {
        queueAudio(data.cipher_audio_url);
    }
    // Backward compatibility
    if (data.audio_url) {
        queueAudio(data.audio_url);
    }
}

// Handle game completion
function handleGameComplete(data) {
    // Remove editor - story is complete
    if (currentEditor) {
        currentEditor.remove();
        currentEditor = null;
    }

    // Display story ending as regular story message (inline, italic)
    appendStoryMessage(data.summary);

    // Play summary audio
    if (data.audio_url) {
        playAudio(data.audio_url);
    }

    // Show cinematic ending animation in right panel
    showAnimation(`
┌────────────────────────────────────────┐
│                                        │
│         🏆 ADVENTURE COMPLETE 🏆       │
│                                        │
│    The story has reached its end...   │
│                                        │
│    Thank you for co-creating with     │
│       CIPHER, your AI storyteller     │
│                                        │
└────────────────────────────────────────┘

                🎭 THE END 🎭
    `);

    // Add final message in left panel
    appendSystemMessage('✨ The adventure has concluded. Thank you for playing Code Raider!');
}

// Create inline contenteditable editor (Word-style)
function createInlineEditor() {
    if (currentEditor) {
        currentEditor.remove();
    }

    const editorDiv = document.createElement('div');
    editorDiv.className = 'inline-editor-word';

    // Contenteditable div for inline typing
    const editableDiv = document.createElement('div');
    editableDiv.className = 'inline-editor-content';
    editableDiv.contentEditable = 'true';
    editableDiv.dataset.placeholder = '💭 Type your thinking here... | Shift+Enter for new line, Enter to send';
    if (isProcessing) {
        editableDiv.contentEditable = 'false';
    }

    // Enter to submit, Shift+Enter for new line
    editableDiv.addEventListener('keydown', (e) => {
        if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            const text = editableDiv.innerText.trim();
            if (text) {
                submitReasoning(text);
            }
        }
    });

    // Auto-focus and cursor blinking
    editorDiv.appendChild(editableDiv);
    chatMessages.appendChild(editorDiv);

    currentEditor = editorDiv;
    editableDiv.focus();
    scrollToBottom();
}

// Submit user reasoning/pseudo-code
function submitReasoning(reasoning) {
    if (isProcessing || !reasoning.trim()) {
        return;
    }

    console.log('💭 User submitted reasoning:', reasoning);

    // Display user's thinking in document
    appendUserMessage(reasoning, '');
    conversationHistory.push({type: 'user', text: reasoning});

    // Remove editor
    if (currentEditor) {
        currentEditor.remove();
        currentEditor = null;
    }

    // Send reasoning to WebSocket (AI will generate code + execute)
    ws.send(JSON.stringify({
        type: 'user_reasoning',
        reasoning: reasoning.trim(),
        conversation_history: conversationHistory
    }));

    isProcessing = true;
}

// UI Helper Functions

function stripAudioTags(text) {
    // Remove all [TAG] patterns from text for display
    // Example: "[MYSTERIOUS] [SLOW] Deep in the dungeon..." → "Deep in the dungeon..."
    return text.replace(/\[([A-Z\s]+)\]\s*/g, '').trim();
}

// Typing effect for story messages (like writing a novel)
function appendStoryMessage(text, audioUrl = null, onComplete = null) {
    const div = document.createElement('div');
    div.className = 'message message-story';
    chatMessages.appendChild(div);

    const cleanText = stripAudioTags(text);
    let charIndex = 0;
    const typingSpeed = 20; // milliseconds per character (faster display)

    function typeNextChar() {
        if (charIndex < cleanText.length) {
            div.textContent = cleanText.substring(0, charIndex + 1);
            charIndex++;
            scrollToBottom();
            setTimeout(typeNextChar, typingSpeed);
        } else {
            // Add replay button after typing completes
            if (audioUrl) {
                const replayBtn = document.createElement('span');
                replayBtn.className = 'audio-replay-btn';
                replayBtn.innerHTML = ' 🔊';
                replayBtn.title = 'Replay story audio';
                replayBtn.style.cssText = 'margin-left: 8px; cursor: pointer; opacity: 0.6; font-size: 14px;';
                replayBtn.onmouseover = () => replayBtn.style.opacity = '1';
                replayBtn.onmouseout = () => replayBtn.style.opacity = '0.6';
                replayBtn.onclick = () => {
                    console.log('🔁 Replaying story audio:', audioUrl);
                    playAudioNow(audioUrl);
                };
                div.appendChild(replayBtn);
            }

            // Call completion callback if provided
            if (onComplete) {
                onComplete();
            }
        }
    }

    typeNextChar();
}

// Typing effect for CIPHER messages
function appendAIMessage(text, audioUrl = null, onComplete = null) {
    const div = document.createElement('div');
    div.className = 'message message-ai';
    chatMessages.appendChild(div);

    const cleanText = stripAudioTags(text);
    const prefix = '🤖 CIPHER: ';
    let charIndex = 0;
    const typingSpeed = 20; // Faster for CIPHER (50 chars/sec)

    function typeNextChar() {
        if (charIndex < cleanText.length) {
            div.innerHTML = `<strong>${prefix}</strong>${cleanText.substring(0, charIndex + 1)}`;
            charIndex++;
            scrollToBottom();
            setTimeout(typeNextChar, typingSpeed);
        } else {
            // Add replay button after typing completes
            if (audioUrl) {
                const replayBtn = document.createElement('span');
                replayBtn.className = 'audio-replay-btn';
                replayBtn.innerHTML = ' 🔊';
                replayBtn.title = 'Replay CIPHER audio';
                replayBtn.style.cssText = 'margin-left: 8px; cursor: pointer; opacity: 0.6; font-size: 14px;';
                replayBtn.onmouseover = () => replayBtn.style.opacity = '1';
                replayBtn.onmouseout = () => replayBtn.style.opacity = '0.6';
                replayBtn.onclick = () => {
                    console.log('🔁 Replaying CIPHER audio:', audioUrl);
                    playAudioNow(audioUrl);
                };
                div.appendChild(replayBtn);
            }

            // Call completion callback if provided
            if (onComplete) {
                onComplete();
            }
        }
    }

    typeNextChar();
}

function appendUserMessage(thought, code) {
    const div = document.createElement('div');
    div.className = 'message message-user';

    let html = '<strong>👤 You:</strong> ';
    if (thought) {
        html += thought;
    }
    if (code) {
        html += '<div class="code-block">' + escapeHtml(code) + '</div>';
    }

    div.innerHTML = html;
    chatMessages.appendChild(div);
    scrollToBottom();
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function appendSystemMessage(text) {
    const div = document.createElement('div');
    div.className = 'message message-system';
    div.textContent = text;
    chatMessages.appendChild(div);
    scrollToBottom();
}

function appendCompleteMessage(text) {
    const div = document.createElement('div');
    div.className = 'message message-complete';
    div.textContent = text;
    chatMessages.appendChild(div);
    scrollToBottom();
}

// Show code in RIGHT panel with syntax highlighting
function showCode(code) {
    // Detect language (default to Python, can add detection logic)
    const language = 'python'; // Currently Python-only game

    // Clear previous code
    codeDisplay.innerHTML = '';

    // Create <pre><code> structure for Prism
    const pre = document.createElement('pre');
    const codeElement = document.createElement('code');
    codeElement.className = `language-${language}`;
    codeElement.textContent = code;

    pre.appendChild(codeElement);
    codeDisplay.appendChild(pre);

    // Apply syntax highlighting (with retry if Prism not loaded yet)
    const applyHighlight = () => {
        if (typeof Prism !== 'undefined') {
            Prism.highlightElement(codeElement);
            console.log('✅ Syntax highlighting applied');
        } else {
            console.warn('⚠️ Prism not loaded yet, retrying in 100ms...');
            setTimeout(applyHighlight, 100);
        }
    };
    applyHighlight();
}

// Test results are now hidden from user - AI uses them internally for story

function showAnimation(animation) {
    // Add separator if there's existing content
    if (animationContent.children.length > 0) {
        const separator = document.createElement('div');
        separator.style.margin = '20px 0';
        separator.style.borderTop = '1px solid #334155';
        separator.style.opacity = '0.3';
        animationContent.appendChild(separator);
    }

    // Create <pre> element for proper ASCII formatting
    const pre = document.createElement('pre');
    pre.style.margin = '0';
    pre.style.padding = '0';
    pre.style.fontFamily = 'inherit';
    pre.style.fontSize = 'inherit';
    pre.style.lineHeight = 'inherit';
    pre.style.color = 'inherit';
    pre.style.whiteSpace = 'pre';

    animationContent.appendChild(pre);

    // Typing effect - reveal character by character
    let charIndex = 0;
    const typingSpeed = 20; // milliseconds per character (faster for appending)

    function typeNextChar() {
        if (charIndex < animation.length) {
            pre.textContent += animation[charIndex];
            charIndex++;

            // Auto-scroll to bottom as animation types
            animationContent.scrollTop = animationContent.scrollHeight;

            setTimeout(typeNextChar, typingSpeed);
        }
    }

    typeNextChar();
}

function scrollToBottom() {
    chatMessages.scrollTop = chatMessages.scrollHeight;
}

// Audio queue system (prevent overlap)
function queueAudio(audioUrl) {
    if (!audioUrl) return;

    console.log('🎵 Queueing audio:', audioUrl);
    audioQueue.push(audioUrl);

    // Start playing if not already playing AND audio is unlocked
    if (!isPlayingAudio && audioUnlocked) {
        playNextAudio();
    } else if (!audioUnlocked) {
        console.log('🔇 Audio queued but waiting for user interaction to unlock autoplay');
        showAudioUnlockPrompt();
    }
}

function showAudioUnlockPrompt() {
    // Show a subtle prompt once
    if (!document.getElementById('audioUnlockPrompt')) {
        const prompt = document.createElement('div');
        prompt.id = 'audioUnlockPrompt';
        prompt.style.cssText = 'position: fixed; top: 20px; left: 50%; transform: translateX(-50%); background: #6366f1; color: white; padding: 12px 24px; border-radius: 8px; z-index: 1000; cursor: pointer; box-shadow: 0 4px 12px rgba(0,0,0,0.2);';
        prompt.innerHTML = '🔊 Click to enable audio';
        prompt.onclick = unlockAudio;
        document.body.appendChild(prompt);
    }
}

function unlockAudio() {
    console.log('🔓 Unlocking audio playback...');
    audioUnlocked = true;

    // Remove prompt
    const prompt = document.getElementById('audioUnlockPrompt');
    if (prompt) {
        prompt.remove();
    }

    // Start playing queued audio immediately
    // The user click is enough to unlock - no dummy audio needed
    console.log('✅ Audio unlocked! Starting playback...');
    if (audioQueue.length > 0 && !isPlayingAudio) {
        playNextAudio();
    }
}

function playNextAudio() {
    if (audioQueue.length === 0) {
        isPlayingAudio = false;
        audioIndicator.style.display = 'none';
        console.log('🔇 Audio queue empty');
        return;
    }

    isPlayingAudio = true;
    const audioUrl = audioQueue.shift(); // Get first in queue

    console.log('▶️ Playing audio:', audioUrl);
    console.log('🎵 Queue remaining:', audioQueue.length);
    audioIndicator.style.display = 'inline-block';
    audioPlayer.src = audioUrl;

    // Play with promise handling
    const playPromise = audioPlayer.play();
    if (playPromise !== undefined) {
        playPromise.then(() => {
            console.log('✅ Audio playback started successfully');
        }).catch(error => {
            console.error('❌ Audio play() failed:', error);
            playNextAudio(); // Skip to next on error
        });
    }

    audioPlayer.onended = () => {
        console.log('✅ Audio finished, playing next...');
        playNextAudio(); // Play next in queue
    };

    audioPlayer.onerror = (error) => {
        console.error('❌ Audio playback error:', error);
        console.error('❌ Audio src:', audioPlayer.src);
        console.error('❌ Audio networkState:', audioPlayer.networkState);
        console.error('❌ Audio readyState:', audioPlayer.readyState);
        playNextAudio(); // Skip to next on error
    };
}

// Play audio immediately (for replay buttons)
function playAudioNow(audioUrl) {
    if (!audioUrl) return;

    // Unlock audio if needed
    if (!audioUnlocked) {
        unlockAudio();
    }

    console.log('▶️ Playing audio immediately:', audioUrl);
    audioIndicator.style.display = 'inline-block';

    // Stop current playback
    audioPlayer.pause();

    // Play new audio
    audioPlayer.src = audioUrl;
    const playPromise = audioPlayer.play();

    if (playPromise !== undefined) {
        playPromise.then(() => {
            console.log('✅ Replay started successfully');
        }).catch(error => {
            console.error('❌ Replay failed:', error);
            audioIndicator.style.display = 'none';
        });
    }

    audioPlayer.onended = () => {
        console.log('✅ Replay finished');
        audioIndicator.style.display = 'none';
    };

    audioPlayer.onerror = () => {
        console.error('❌ Replay error');
        audioIndicator.style.display = 'none';
    };
}

// Backward compatibility - deprecated
function playAudio(audioUrl) {
    queueAudio(audioUrl);
}

// Initialize
connectWebSocket();
