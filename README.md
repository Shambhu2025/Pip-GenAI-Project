# Pip: An Offline Generative AI Voice Companion for Android

**Course Project:** CS787 — Generative Artificial Intelligence, IIT Kanpur  
**Author:** Shambhu Shivanand Khanai  
**Project Report (PDF):** [Pip\_Offline\_GenAI\_Voice\_Companion\_Report.pdf](https://www.google.com/search?q=./Pip_Offline_GenAI_Voice_Companion_Report.pdf)  
**Repository:** [https://github.com/Shambhu2025/Pip-GenAI-Project](https://github.com/Shambhu2025/Pip-GenAI-Project)

---

## 1. Overview

**Pip** is a 100% offline, privacy-preserving multimodal voice companion for `arm64-v8a` Android devices. It couples a real-time neural speech front-end, a **4-bit NF4 QLoRA fine-tuned `Qwen/Qwen2.5-1.5B-Instruct`** Small Language Model lowered to an 8-bit dynamic **LiteRT-LM (`.litertlm`)** execution graph, and a parallel audio-visual embodiment back-end driving **60 Hz procedural Jetpack Compose Canvas kinematics** alongside **Piper VITS** neural speech synthesis and RMS audio lip-sync.

### Key Highlights
* **100% Offline Execution:** Zero cloud API calls, zero network latency jitter, and full local privacy.
* **All-Layer QLoRA Adaptation (`18.46M` trainable params):** Fine-tuned across all 7 linear projections (`q_proj`, `k_proj`, `v_proj`, `o_proj`, `gate_proj`, `up_proj`, `down_proj`) in all 28 Transformer blocks (`r = 16, alpha = 32`).
* **Dual-Stream Multimodal Output:** Emits one of 9 bracketed control tags (`[happy]`, `[curious]`, `[surprised]`, `[sad]`, `[sleepy]`, `[thinking]`, `[dance]`, `[sing]`, `[laugh]`) on Token #1 to trigger procedural Canvas animations while streaming sentence chunks to Piper VITS (`1.20x` speed).
* **Sub-1.6s Voice Turnaround:** Achieves **1,567 ms mean E2E latency** (`1,008 ms` overall / `971 ms` warm Time-to-First-TTS-Clause at `25.4 tokens/sec`) and **100% automated formatting compliance (`4.71 / 5.00` adjusted composite rubric score)** on Snapdragon 8 Gen 2 (`SM-X710`).
* **Proactive Idle Companion & Lifecycle Safety:** Automatically initiates context-aware check-ins after 10 seconds of room silence (up to 10 consecutive rounds) and cleanly halts audio/microphone threads when backgrounded or screen-locked.

---

## 2. System Architecture & Runtime Stack

| Stage | Module / Source File | Model / Engine | Precision & Footprint | Mean Latency |
| :--- | :--- | :--- | :--- | :--- |
| **1. Audio Capture** | `audio/MicRecorder.kt` | Android `AudioRecord` (16 kHz mono PCM) | 16-bit PCM (`32 ms` frames) | Real-time |
| **2. Voice Activity & Barge-In** | `speech/Vad.kt` | `silero_vad.onnx` (`sherpa-onnx v1.12.28`) | `FP32` ONNX (`2 MB`) | `< 1 ms/frame` |
| **3. Offline STT** | `speech/SpeechToText.kt` | `sherpa-onnx-whisper-tiny.en` | `INT8` ONNX (`110 MB`) | `177.0 ms` (spoken) |
| **4. Homophone Normalizer** | `llm/PromptBuilder.kt` | Phrase-anchored Indian-English regex | Deterministic Kotlin | `< 1 ms` |
| **5. Generative SLM Brain** | `llm/MediaPipeEngine.kt` | QLoRA `Qwen2.5-1.5B-Instruct` (`LiteRT-LM 0.10.0`) | `dynamic_wi8_afp32` (`1,570 MB` + `~60 MB` KV) | `1,008 ms` TTFT (`25.4 tok/s`) |
| **6. Procedural Embodiment** | `ui/FaceScreen.kt`, `FaceAnimator.kt` | 11-State Jetpack Compose Canvas (9 LLM tags + `LISTENING`, `NEUTRAL`) | 60 Hz vsync (`withFrameNanos`) | Instant on Token #1 |
| **7. Neural TTS & Lip-Sync** | `speech/TextToSpeech.kt`, `LoudnessMeter.kt` | `vits-piper-en_US-amy-low` (`1.20x`) + PCM RMS envelope | `FP32` ONNX (`63 MB`) | `398.7 ms` |

---

## 3. Repository Structure

```text
Pip-GenAI-Project/
├── Pip_Offline_GenAI_Voice_Companion_Report.pdf   # Full CS787 Academic Project Report
├── README.md                                      # Project documentation & run guide
├── build.gradle.kts / settings.gradle.kts         # Gradle build configuration
├── app/
│   ├── build.gradle.kts                           # App dependencies (LiteRT-LM 0.10.0, Compose, Coroutines)
│   ├── libs/sherpa-onnx-1.12.28.aar               # Offline ONNX Runtime JNI bindings for VAD, Whisper, VITS
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── java/com/example/companion/
│           ├── MainActivity.kt                    # Lifecycle observer, screen wake-lock, Compose entry point
│           ├── audio/
│           │   ├── LoudnessMeter.kt               # Real-time PCM RMS energy calculation for mouth lip-sync
│           │   └── MicRecorder.kt                 # 16 kHz mono PCM frame capture
│           ├── core/
│           │   ├── AppState.kt                    # State machine (LOADING, IDLE, LISTENING, THINKING, SPEAKING)
│           │   ├── ConversationManager.kt         # Dual-stream orchestrator, barge-in, E2E telemetry logger
│           │   ├── Emotion.kt                     # 9 LLM control tags + 2 system states (LISTENING, NEUTRAL)
│           │   └── IdleScheduler.kt               # 10s proactive question/action initiator (max 10 rounds)
│           ├── download/
│           │   └── ModelDownloader.kt             # First-launch resilient downloader & tar.bz2 extractor
│           ├── llm/
│           │   ├── LlmEngine.kt                   # Streaming LLM interface
│           │   ├── MediaPipeEngine.kt             # LiteRT-LM 0.10.0 native wrapper & 2-turn KV session reset
│           │   └── PromptBuilder.kt               # Qwen ChatML prompt builder & Whisper STT regex normalizer
│           ├── speech/
│           │   ├── SpeechToText.kt                # Offline Whisper tiny.en encoder-decoder wrapper
│           │   ├── TextToSpeech.kt                # Offline Piper VITS wrapper & PCM playback callback
│           │   └── Vad.kt                         # Silero VAD speech segmentation & barge-in detector
│           └── ui/
│               ├── FaceAnimator.kt                # 60 Hz procedural kinematics (breathing, dance, laugh, notes)
│               └── FaceScreen.kt                  # Custom Compose Canvas UI & download progress screen
└── scripts/                                       # QLoRA dataset generation, Kaggle T4 training & LiteRT export
```

---

## 4. How to Build and Run the Android App

### Prerequisites
1. **Hardware:** An `arm64-v8a` Android phone or tablet with **6 GB+ RAM** (tested on Samsung Galaxy Tab S9 `SM-X710`, Snapdragon 8 Gen 2, Android 16) and **~2.5 GB of free storage**.
2. **Development Machine:**
   * **JDK 17** (e.g., `D:\Java` or Android Studio bundled JBR)
   * **Android SDK (API 35)** with `platform-tools` (`adb`) enabled
   * **Android Studio Ladybug / Meerkat** or **Gradle 8.x CLI**

---

### Step 1: Clone the Repository
```powershell
git clone https://github.com/Shambhu2025/Pip-GenAI-Project.git
cd Pip-GenAI-Project
```

### Step 2: Configure Local SDK Path (If Building via CLI)
Create a `local.properties` file in the project root pointing to your Android SDK directory:
```properties
sdk.dir=D\:\\Android\\Sdk
```
*(If your JDK 17 is in a custom directory such as `D:\Java`, set `$env:JAVA_HOME = "D:\Java"` in PowerShell before running Gradle.)*

### Step 3: Build and Install the APK on Your Connected Device
Enable **USB Debugging** on your Android device, connect it via USB, verify it appears in `adb devices`, and run:
```powershell
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
*(Alternatively, open the project folder in **Android Studio** and click **Run `app`**.)*

---

### Step 4: Provision the Offline Neural Models (Choose Option A or Option B)

All four neural models reside in the app's external files directory on the Android device:
```text
/sdcard/Android/data/com.example.companion/files/models/
├── silero_vad.onnx
├── sherpa-onnx-whisper-tiny.en/
├── vits-piper-en_US-amy-low/
└── qwen25-1.5b-pip-qlora.litertlm
```

#### Option A: Automatic First-Launch Download (Recommended)
1. Connect the Android device to Wi-Fi for the **initial setup only**.
2. Launch **Offline Speech Lab** on the device and grant **Microphone (`RECORD_AUDIO`)** permission when prompted.
3. On first launch, `ModelDownloader.kt` automatically downloads and extracts `silero_vad.onnx`, `sherpa-onnx-whisper-tiny.en`, `vits-piper-en_US-amy-low`, and the fine-tuned `.litertlm` model into `/sdcard/Android/data/com.example.companion/files/models/` with a live progress bar.
4. Once initialization completes, you can **turn off Wi-Fi / enable Airplane Mode** — Pip runs 100% offline from that point onward.

#### Option B: Manual Offline Push via `adb` (Zero Device Internet Required)
If you already have the exported `.litertlm` and ONNX speech models on your PC, push them directly over USB:
```powershell
adb shell mkdir -p /sdcard/Android/data/com.example.companion/files/models
adb push silero_vad.onnx /sdcard/Android/data/com.example.companion/files/models/
adb push sherpa-onnx-whisper-tiny.en /sdcard/Android/data/com.example.companion/files/models/
adb push vits-piper-en_US-amy-low /sdcard/Android/data/com.example.companion/files/models/
adb push qwen25-1.5b-pip-qlora.litertlm /sdcard/Android/data/com.example.companion/files/models/
```

---

### Step 5: Interacting with Pip & Inspecting Telemetry Logs
1. **Speak Naturally:** Talk to Pip once the status indicator shows **Listening**. Pip will transition its procedural face on Token #1 (`[happy]`, `[dance]`, `[sing]`, `[laugh]`, `[curious]`, `[surprised]`, `[sad]`, `[thinking]`, `[sleepy]`) and speak back via Piper VITS with real-time RMS mouth lip-sync.
2. **Voice Barge-In:** Speak while Pip is talking to interrupt TTS playback immediately (`Vad.kt` $\to$ `stopSpeaking()`).
3. **Proactive Idle Check-Ins:** Stay silent for `10 seconds` — `IdleScheduler.kt` will autonomously initiate a friendly question or action (up to 10 consecutive rounds).
4. **Pull On-Device Evaluation & Latency Logs:** Every turn logs raw Whisper transcripts, normalized prompts, Pip's raw tagged response, `E2E` latency, `TTFT` (Time-to-First-TTS-Clause), and decode speed (`tok/s`) to `dialogue_history.txt`:
   ```powershell
   adb pull /sdcard/Android/data/com.example.companion/files/dialogue_history.txt .
   adb logcat -s ConversationManager:I MediaPipeEngine:I IdleScheduler:I
   ```

---

## 5. Reproducing the QLoRA Fine-Tuning & `.litertlm` Export Pipeline

If you wish to retrain the QLoRA adapters or re-export the `.litertlm` package from scratch:

1. **Generate the Multi-Scenario ChatML Dataset (`880` train / `100` val dialogues):**
   ```bash
   python scripts/generate_dataset.py
   ```
2. **Run 4-Bit NF4 QLoRA Fine-Tuning (`r=16, alpha=32`, all 7 linear layers, 110 steps on Kaggle T4 GPU):**
   * Upload `train.jsonl`, `val.jsonl`, and `scripts/train_qlora_kaggle.py` to a Kaggle notebook with a **Tesla T4 GPU** (`transformers==4.46.3`, `peft==0.13.2`, `bitsandbytes==0.44.1`).
   * Run training (~6 minutes) and merge the learned adapters into the base model via `merge_and_unload()`.
3. **Lower Merged Checkpoint to `dynamic_wi8_afp32` `.litertlm`:**
   * Using `litert-torch==0.4.0`, run the export script with `experimental_lightweight_conversion=True` and `externalize_embedder=True` to bypass the 2 GB `pybind11` FlatBuffer limit, and inject the Qwen ChatML `LlmMetadataProto` template (`max_num_tokens: 512`, stop tokens `151643` and `151645`).

---

## 6. Citation

If you reference this project or report, please cite:
```bibtex
@techreport{khanai2026pip,
  author      = {Khanai, Shambhu Shivanand},
  title       = {Pip: An Offline Generative AI Voice Companion via QLoRA Fine-Tuning of a 1.5B Small Language Model},
  institution = {Department of Statistics and Data Science, Indian Institute of Technology Kanpur (CS787)},
  year        = {2026},
  url         = {https://github.com/Shambhu2025/Pip-GenAI-Project}
}
```
