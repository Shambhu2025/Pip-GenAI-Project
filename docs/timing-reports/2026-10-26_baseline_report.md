# Step 3 Baseline Timing & Memory Report
- **Date**: 2026-10-26 18:14:16
- **Device**: samsung SM-X710 (SDK 36)
- **Models**: Silero VAD + Whisper Tiny.en (int8) + Qwen3-0.6B (INT4) + Piper en_US-amy-low (1.20x)
- **Cold Start (All 4 Models Loaded)**: 1889 ms
- **Peak App RAM (PSS)**: 1231 MB (Steady: 1231 MB)
- **Temperature**: Start 35.2 °C -> Current 35.4 °C
- **Average End-of-Speech to First-Sound**: **1613 ms** (STT: 280 ms | LLM TTFT: 727 ms | First TTS: 238 ms | Speed: 4.2 tok/s)

| Turn | STT (ms) | LLM TTFT (ms) | 1st Sent LLM (ms) | 1st TTS (ms) | **End-to-First-Sound (ms)** | Speed (tok/s) | RAM (MB) | Temp (°C) | User -> Reply |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| 5 | 280 | 557 | 1068 | 354 | **1706** | 4.2 | 1231 | 35.4 | "Tell me a silly joke!" -> "[curious] Did you know that sea otters hold hands while they sleep so they don't float apart? Pretty cute, right?" |
| 4 | 280 | 552 | 1050 | 331 | **1663** | 4.2 | 1226 | 35.4 | "I feel a bit bored today." -> "[sleepy] I feel a bit bored today, and I'm going to take a break from my phone! Let me feel the world around me!" |
| 3 | 280 | 560 | 768 | 107 | **1158** | 4.2 | 1202 | 35.4 | "Why do cats purr?" -> "[curious] Why do cats purr? Because they're super into their own little world! Pretty cool, I think!" |
| 2 | 280 | 558 | 971 | 282 | **1535** | 4.2 | 1202 | 35.4 | "I just finished my homework!" -> "[sleepy] I'm done with my homework, just like I was doing all day. Let me take a break and sleep!" |
| 1 | 280 | 1411 | 1598 | 120 | **2003** | 4.3 | 1183 | 35.4 | "Hey Pip, good morning!" -> "[curious] Oh, good morning! Are you still on your phone? I'm super excited for your screen!" |
