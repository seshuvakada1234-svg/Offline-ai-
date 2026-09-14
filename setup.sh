#!/usr/bin/env bash
set -e

echo "=========================================="
echo " Setting up MyAI Offline Voice Assistant  "
echo "=========================================="

# 1. Initialize git submodules for llama.cpp and whisper.cpp
if [ -d ".git" ]; then
    echo "--> Initializing Git Submodules (llama.cpp & whisper.cpp)..."
    git submodule update --init --recursive
else
    echo "--> Note: Not in a git repo root, skipping submodule checkout."
fi

# 2. Make gradlew executable
if [ -f "android/gradlew" ]; then
    echo "--> Making android/gradlew executable..."
    chmod +x android/gradlew
fi

# 3. Download Sherpa-ONNX AAR if needed
mkdir -p android/app/libs
AAR_FILE="android/app/libs/sherpa-onnx-1.13.7.aar"
if [ ! -s "$AAR_FILE" ]; then
    echo "--> Downloading sherpa-onnx-1.13.7.aar for offline TTS..."
    curl -L -f -o "$AAR_FILE" "https://github.com/k2-fsa/sherpa-onnx/releases/download/v1.13.7/sherpa-onnx-1.13.7.aar"
    echo "--> Sherpa-ONNX AAR downloaded successfully."
else
    echo "--> Sherpa-ONNX AAR already present."
fi

# 4. Install Web Showcase dependencies
if [ -f "package.json" ]; then
    echo "--> Installing Web showcase dependencies..."
    npm install
fi

echo ""
echo "=========================================="
echo " Setup complete!"
echo " • To build Android APK:  cd android && ./gradlew assembleDebug"
echo " • To run Web Showcase:   npm run dev"
echo "=========================================="
