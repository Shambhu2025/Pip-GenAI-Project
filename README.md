# Pip: An Offline GenAI Voice Companion

**CS787 (Generative AI) Project — Indian Institute of Technology Kanpur (IITK)**

Pip is a **100% offline, voice-only animated cartoon companion** for Android powered by a **QLoRA fine-tuned 1.54B Small Language Model (`Qwen/Qwen2.5-1.5B-Instruct`)**, **8-bit dynamic quantization (`dynamic_wi8_afp32`)**, **offline Transformer STT (`Whisper tiny.en`)**, **generative neural TTS (`Piper VITS`)**, and a **60 FPS procedural Jetpack Compose Canvas character**.

---

## 1. Key Highlights & Verified On-Device Metrics (`SM-X710`)
- **1.57s Average End-to-End Voice Turnaround**: `177 ms` STT (`Whisper tiny.en INT8`) + `1,008 ms` LLM TTFT + `399 ms` TTS (`Piper VITS 1.20x`).
- **25.4 tokens/sec On-Device Decode Speed**: ~6x faster than human speaking rate, running 100% offline via Google AI Edge `LiteRT-LM (0.18.0)`.
- **72% Model Size Reduction**: Lowered from `6.10 GB` (FP32) to a **`1.70 GB` `.litertlm`** package using `dynamic_wi8_afp32` (8-bit weights, 32-bit dynamic activations) and an externalized token embedder.
- **5.00 / 5.0 Post-QLoRA Rubric Score** (up from `3.49 / 5.0` untuned baseline): 100% first-token `[emotion/action]` tag accuracy, 100% spoken brevity compliance (`avg 17.6 words`), and 0% leaked emojis/markdown.

---

## 2. System Architecture

| Stage | Module / File | Neural Network Architecture | Precision & Size | On-Device Latency |
| :--- | :--- | :--- | :--- | :--- |
| **1. Audio Capture** | `audio/MicRecorder.kt` | 16 kHz Mono PCM (512-sample windows) | 16-bit PCM -> FP32 | `32 ms` window |
| **2. Voice Activity (VAD)** | `audio/Vad.kt` | Silero VAD (`silero_vad.onnx`) | FP32 ONNX (`~2 MB`) | `< 1 ms / frame` |
| **3. Speech-to-Text (STT)** | `audio/SpeechToText.kt` | OpenAI Whisper `tiny.en` (39M params) | INT8 ONNX (`~110 MB`) | **`177 ms` avg** |
| **4. Core GenAI Brain** | `llm/MediaPipeEngine.kt` | QLoRA `Qwen2.5-1.5B-Instruct` (28 layers) | `dynamic_wi8_afp32` (`~1.70 GB`) | **`1,008 ms` TTFT • `25.4 tok/s`** |
| **5. Neural Speech (TTS)** | `audio/TextToSpeech.kt` | Piper VITS `en_US-amy-low` (VAE + HiFi-GAN) | FP32 ONNX (`~63 MB`) | **`399 ms` avg** |
| **6. Embodiment & Lip-Sync** | `face/FaceScreen.kt` | 11-State Procedural Compose Canvas + RMS Lip-Sync | 60 FPS Vector Canvas | `16.6 ms / frame` |

---

## 3. Before vs. After QLoRA Fine-Tuning Benchmark

Fine-tuned `Qwen/Qwen2.5-1.5B-Instruct` using **4-bit NormalFloat (`NF4`) QLoRA** ([Dettmers et al., NeurIPS 2023](https://arxiv.org/abs/2305.14314)) with double quantization, `r=16, lora_alpha=32` across all 7 linear projections (`q_proj`, `k_proj`, `v_proj`, `o_proj`, `gate_proj`, `up_proj`, `down_proj`), and `paged_adamw_8bit`. Training loss converged from **`2.049` to `0.0061`**.

| Evaluation Metric | Before: Untuned Baseline (`0.6B`) | After: QLoRA Fine-Tuned (`1.5B`) | Improvement |
| :--- | :--- | :--- | :--- |
| **Overall Rubric Score (out of 5.0)** | `3.49 / 5.0` | **`5.00 / 5.0`** | **`+1.51 pts`** |
| **First-Token `[tag]` Accuracy** | `38.0%` | **`100.0%` (`21/21` turns)** | **`+62.0%`** |
| **Spoken Brevity (`<= 35` words)** | `64.8%` | **`100.0%` (`17.6` words avg)** | **`+35.2%`** |
| **Emoji-Free & Markdown-Free Output** | `72.2%` | **`100.0%` (`0` leaked)** | **`+27.8%`** |
| **Avg End-to-End Voice Latency** | `~2,450 ms` | **`1,567 ms` (`1.57 s`)** | **`36% Faster`** |

---

## 4. Repository Structure
- `app/src/main/java/com/example/companion/`
  - `core/` — `ConversationManager.kt` (full-duplex voice loop & barge-in), `IdleScheduler.kt` (proactive idle initiation), `PromptBuilder.kt` (persona & STT homophone normalization).
  - `audio/` — `MicRecorder.kt`, `Vad.kt` (Silero VAD), `SpeechToText.kt` (Whisper INT8), `TextToSpeech.kt` (Piper VITS), `LoudnessMeter.kt` (RMS lip-sync).
  - `llm/` — `LlmEngine.kt`, `MediaPipeEngine.kt` (LiteRT-LM `.litertlm` wrapper & 2-turn sliding KV cache), `ReplyLimits.kt`.
  - `face/` — `Emotion.kt` (11 affective/kinematic states including `[dance]`, `[sing]`, `[laugh]`), `FaceAnimator.kt`, `FaceScreen.kt`.
  - `models/` — `ModelPaths.kt`, `ModelDownloader.kt` (offline bundle & `StatFs` storage verifier).
- `qlora_training/` — QLoRA evaluation suite, dataset samples, and verified on-device dialogue history (`dialogue_history.txt`).
- `docs/timing-reports/` — Baseline and post-QLoRA on-device timing & rubric comparison reports.
