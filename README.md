# MyAI: 100% Offline Voice & Text AI Assistant

[![Android Build](https://github.com/seshuvakada/MyAI/actions/workflows/android.yml/badge.svg)](https://github.com/seshuvakada/MyAI/actions/workflows/android.yml)
[![Platform](https://img.shields.io/badge/Platform-Android_8.0+_(API_26+)-brightgreen.svg)](https://developer.android.com)
[![Language](https://img.shields.io/badge/Kotlin-1.9.23-purple.svg)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/Jetpack_Compose-Material_3-blue.svg)](https://developer.android.com/jetpack/compose)
[![Inference Engine](https://img.shields.io/badge/C++17-llama.cpp_&_whisper.cpp-orange.svg)](https://github.com/ggml-org/llama.cpp)
[![Privacy](https://img.shields.io/badge/Privacy-100%25_Offline_Local_Storage-green.svg)](#privacy--security)

**MyAI** is a private, zero-latency, 100% offline AI voice and text assistant for Android. It runs quantized local language models (such as **Qwen3 1.7B** and **Phi-4 Mini**) directly on your device CPU/DSP using an optimized **llama.cpp** C++ engine, performs offline speech recognition with **Whisper.cpp**, speaks offline via **Sherpa-ONNX TTS**, and executes smart device actions (flashlight, timers, opening apps, YouTube search) without relying on any external cloud server.

---

## 🌟 Key Features

- 🧠 **100% On-Device LLM Inference**: Runs GGUF models locally with native multi-threaded CPU acceleration (ARM NEON & fp16 vector intrinsics) via `llama.cpp`.
- 🎙️ **Offline Speech-to-Text (STT)**: Real-time, on-device audio transcription powered by `whisper.cpp` (`ggml-tiny.bin` / `ggml-base.bin`).
- 🔊 **Offline Text-to-Speech (TTS)**: Natural neural voice output on-device powered by Sherpa-ONNX.
- ⚡ **Instant Offline Math Evaluator**: Mathematical operations (e.g. `5899948990 + 23568909876`) evaluate in `< 1ms` using a local parser, bypassing the LLM to conserve battery.
- 📱 **Smart Device Actions**: Natural language triggers Android system intents:
  - Flashlight on/off
  - Setting countdown timers and alarms
  - Opening installed applications (YouTube, Camera, Settings, etc.)
  - Querying device status (battery, network connectivity, storage)
- 🗄️ **Local SQLite (Room) Database**: Complete chat history and model configurations persist exclusively on your phone.
- 🛡️ **Zero Data Tracking**: No telemetry, no external API calls, and no cloud dependency.

---

## 📁 Repository Structure

```text
├── android/                             # Android Studio Project (Kotlin & Jetpack Compose)
│   ├── app/
│   │   ├── src/main/
│   │   │   ├── cpp/                     # C++ Native JNI code
│   │   │   │   ├── llama-jni.cpp        # llama.cpp JNI wrapper with thread-safe abort callbacks
│   │   │   │   ├── whisper-jni.cpp      # whisper.cpp audio transcription JNI bridge
│   │   │   │   ├── CMakeLists.txt       # CMake build script for libmyai_native.so
│   │   │   │   └── third_party/         # Git submodules (llama.cpp & whisper.cpp)
│   │   │   ├── java/com/myai/offline/   # Kotlin application source
│   │   │   │   ├── actions/             # Action intent parser & system dispatcher
│   │   │   │   ├── data/                # Room database, DAO, entity definitions
│   │   │   │   ├── llm/                 # Local LLM engine, prompt formatters, native bridge
│   │   │   │   ├── ui/                  # Jetpack Compose UI (ChatScreen, VoiceSheet, Models)
│   │   │   │   ├── viewmodel/           # MainViewModel & state orchestrator
│   │   │   │   └── voice/               # Audio recorder & offline Whisper/Sherpa bridges
│   │   │   └── AndroidManifest.xml
│   │   └── build.gradle.kts
│   ├── build.gradle.kts
│   └── gradlew                          # Gradle wrapper
├── src/                                 # Web Simulator & AI Studio Interactive Harness
│   ├── components/                      # React Material/Tailwind UI components
│   ├── services/                        # Simulated engine, model manager & test runner
│   └── App.tsx                          # Interactive Web preview application
├── .github/
│   └── workflows/
│       └── android.yml                  # CI workflow: Unit tests, lint, and debug APK build
├── setup.sh                             # One-command developer environment setup script
├── package.json                         # Web showcase build configuration
└── metadata.json                        # AI Studio applet metadata
```

---

## 🚀 Quick Start (GitHub)

### 1. Clone with Submodules

This repository uses Git submodules for `llama.cpp` and `whisper.cpp`. Clone recursively:

```bash
git clone --recurse-submodules https://github.com/<your-username>/<your-repo-name>.git
cd <your-repo-name>
```

If you already cloned without submodules:

```bash
git submodule update --init --recursive
```

### 2. Run the One-Step Setup Script

Run the included automated setup script to verify submodules, set permissions, and download required libraries:

```bash
chmod +x setup.sh
./setup.sh
```

---

## 🔨 Building the Android App

### Prerequisites

- **Java Development Kit**: JDK 17 (Temurin or OpenJDK recommended)
- **Android SDK**: Compile SDK 34, Min SDK 26 (Android 8.0 Oreo or higher)
- **Android NDK**: Version 26.x or 27.x
- **CMake**: 3.22.1+

### Build via Command Line

```bash
cd android

# Run unit tests
./gradlew testDebugUnitTest

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

The compiled APK will be located at:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

### Install onto Device via ADB

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 🤖 Supported Models (GGUF)

Transfer any quantized `.gguf` file to your Android device storage (or download within the in-app Model Manager):

| Model | Recommended Quantization | Memory (RAM) Required | Speed (Mobile CPU) |
|---|---|---|---|
| **Qwen3 1.7B** | `q4_k_m` (~1.1 GB) | ~1.8 GB | ~12–18 tokens/sec |
| **Qwen3 4B** | `q4_k_m` (~2.4 GB) | ~3.4 GB | ~6–10 tokens/sec |
| **Phi-4 Mini (3.8B)** | `q4_k_m` (~2.3 GB) | ~3.3 GB | ~6–11 tokens/sec |
| **Whisper Tiny** | `ggml-tiny.bin` (~75 MB) | ~200 MB | Real-time |

---

## 🌐 Running the Web Simulator in AI Studio

To test the application UI, voice interactions, prompt formatters, and action dispatchers directly in your browser:

```bash
# Install dependencies
npm install

# Start local dev server (port 3000)
npm run dev

# Run TypeScript verification
npm run lint

# Build static production bundle
npm run build
```

---

## 📤 How to Push Changes to GitHub

If you are exporting or syncing this project from Google AI Studio to your GitHub repository:

```bash
# Initialize git if not already present
git init

# Add remote origin
git remote add origin https://github.com/<your-username>/<your-repo-name>.git

# Stage all files
git add .

# Commit your changes
git commit -m "feat: MyAI offline voice assistant with llama.cpp and Whisper"

# Set default branch to main and push
git branch -M main
git push -u origin main
```

---

## 🔒 Privacy & Security

- **No Remote Telemetry**: MyAI contains no tracking pixels, analytics SDKs, or cloud proxies.
- **Local Voice Buffer**: Audio sampled from the microphone stays in RAM during transcription and is discarded immediately after.
- **Local Storage**: All conversation history is stored strictly in the private app sandbox (`/data/data/com.myai.offline/databases/myai.db`).

---

## 📄 License

This project is licensed under the Apache License 2.0. Third-party components (`llama.cpp`, `whisper.cpp`, and `sherpa-onnx`) are subject to their respective open-source licenses (MIT / Apache 2.0).
