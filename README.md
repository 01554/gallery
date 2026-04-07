# Gallery + OpenAI-Compatible API Server (Fork)

> **[日本語 README](README.ja.md)**

> **This fork adds a localhost HTTP server to Google AI Edge Gallery, enabling OpenAI-compatible API access to on-device GPU-accelerated LLM inference via `curl` or any OpenAI client.**

## What this fork adds

Google AI Edge Gallery is an Android app that runs LLMs on-device using TFLite GPU delegate. This fork adds a **Foreground Service** that exposes the inference engine as an **OpenAI-compatible HTTP API** on `localhost:8080`.

This means you can run Gemma 4 (or other LiteRT models) on your phone's GPU and access it from Termux, scripts, or any app that speaks OpenAI API.

### Why?

On devices with Qualcomm Adreno 6xx GPUs (e.g. Snapdragon 888), llama.cpp's Vulkan and OpenCL backends both crash due to driver incompatibilities. TFLite GPU delegate is the only reliable way to do GPU inference on these devices.

### Added files

| File | Description |
|------|-------------|
| `app/.../server/LlmHttpServer.kt` | NanoHTTPD server with `/v1/chat/completions`, `/v1/models`, `/health` |
| `app/.../server/LlmServerService.kt` | Foreground Service that loads model with `Backend.GPU()` and starts HTTP server. Exposes server state via `StateFlow` |
| `app/.../server/ServerDrawerItem.kt` | Navigation drawer item for server start/stop with color-coded status |
| `app/.../server/ServerButton.kt` | Floating action button (kept for reference, not used in current UI) |

### Modified files

| File | Change |
|------|--------|
| `app/build.gradle.kts` | Added `nanohttpd:2.3.1` dependency |
| `AndroidManifest.xml` | Added Service registration and `FOREGROUND_SERVICE_SPECIAL_USE` permission |
| `HomeScreen.kt` | Added "API Server" item to the navigation drawer (alongside Settings and Models) |

## Quick Start

### 1. Build and install

```bash
# Requires Java 21 and Android SDK
export JAVA_HOME=/path/to/openjdk-21
cd Android/src
echo "sdk.dir=/path/to/android-sdk" > local.properties
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. Download a model

Open the app, tap the **hamburger menu** (top-left), then tap **Models**. Download a model from the list (e.g. **Gemma 4 E2B**). The server will use models downloaded here.

Alternatively, you can push a `.litertlm` file manually:

```bash
curl -L "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm" \
  -o gemma-4-E2B-it.litertlm
adb push gemma-4-E2B-it.litertlm /data/local/tmp/
```

### 3. Start the server (from the app UI)

Open the **hamburger menu** (top-left). You'll see **"API Server"** below Settings and Models.

| Server status | Color | Action |
|---------------|-------|--------|
| Stopped | Gray border & icon | Tap to start |
| Loading model | Orange border & icon | Wait for model to load |
| Running | Green border & icon (shows port) | Tap to stop |
| Error | Red border & icon | Tap to retry |

When you tap "API Server":
- If **one model** is available, the server starts immediately
- If **multiple models** are available, a picker dialog lets you choose which model to load
- The server loads the model onto the **GPU** and starts listening on `localhost:8080`

Models are discovered from two sources:
- Models downloaded via the app's **Models** page
- `.litertlm` files in `/data/local/tmp/`

### 4. Start the server (via adb, alternative)

You can also start/stop the server from the command line:

```bash
# Start
adb shell am start-foreground-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService \
  --es model_path '/data/local/tmp/gemma-4-E2B-it.litertlm' \
  --ei port 8080

# Stop
adb shell am stop-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService
```

### 5. Use it

From Termux or any app on the device:

```bash
# Health check
curl http://127.0.0.1:8080/health

# Chat completion (OpenAI-compatible)
curl -X POST http://127.0.0.1:8080/v1/chat/completions \
  -H "Content-Type: application/json" \
  -d '{
    "model": "gemma",
    "messages": [{"role": "user", "content": "Hello!"}],
    "max_tokens": 128
  }'
```

### Compatible models

Only official `litert-community` models are confirmed to work:

| Model | Size | Min RAM | Download |
|-------|------|---------|----------|
| Gemma 4 E2B | 2.4GB | 8GB | [litert-community/gemma-4-E2B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm) |
| Gemma 3 1B | 557MB | 6GB | [litert-community/Gemma3-1B-IT](https://huggingface.co/litert-community/Gemma3-1B-IT) |
| Gemma 4 E4B | 3.4GB | 12GB | [litert-community/gemma-4-E4B-it-litert-lm](https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm) |

Third-party `.litertlm` files may crash. Stick with `litert-community` or `google` published models.

### Tested on

- Galaxy Z Flip3 (Snapdragon 888 / Adreno 660, 8GB RAM, Android 14)
- Gemma 4 E2B: GPU usage ~40%, model load ~12s

### Build environment (confirmed working)

| Item | Version |
|------|---------|
| Host OS | macOS 15.6.1 (Apple Silicon / arm64) |
| Java | OpenJDK 21.0.10 (Homebrew) |
| Gradle | 8.10.2 |
| Kotlin | 1.9.24 |
| Android SDK | compileSdk 35, minSdk 31, targetSdk 35 |
| NDK | 27.2.12479018 |

**Note:** Java 17 does NOT work (Hilt/KAPT throws `NullPointerException: processingEnv must not be null`). Use Java 21.

### Target device (confirmed working)

| Item | Detail |
|------|--------|
| Device | Galaxy Z Flip3 (SM-F711N) |
| SoC | Snapdragon 888 |
| GPU | Adreno 660 |
| RAM | 8GB |
| OS | Android 14 |
| Model | Gemma 4 E2B (2.4GB) |
| GPU usage | ~40% during inference |
| Model load time | ~12 seconds |

### Stop the server

```bash
adb shell am stop-service \
  -n com.google.aiedge.gallery/com.google.ai.edge.gallery.server.LlmServerService
```

---

# Google AI Edge Gallery

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![GitHub release (latest by date)](https://img.shields.io/github/v/release/google-ai-edge/gallery)](https://github.com/google-ai-edge/gallery/releases)

**Explore, Experience, and Evaluate the Future of On-Device Generative AI with Google AI Edge.**

AI Edge Gallery is the premier destination for running the world's most powerful open-source Large Language Models (LLMs) on your mobile device. Experience high-performance Generative AI directly on your hardware—fully offline, private, and lightning-fast.

**Now Featuring: Gemma 4**

The latest version brings official support for the newly released Gemma 4 family. As the centerpiece of this release, Gemma 4 allows you to test the cutting edge of on-device AI. Experience advanced reasoning, logic, and creative capabilities without ever sending your data to a server.


| **Install the app today from Google Play** | **Install the app today from App Store** |
| :--- | :--- |
| <a href='https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery'><img alt='Get it on Google Play' height="120" src='https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png'/></a> | <a href="https://apps.apple.com/us/app/google-ai-edge-gallery/id6749645337?itscg=30200&itsct=apps_box_badge&mttnsubad=6749645337" style="display: inline-block;"> <img src="https://toolbox.marketingtools.apple.com/api/v2/badges/download-on-the-app-store/black/en-us?releaseDate=1771977600" alt="Download on the App Store" style="width: 246px; height: 90px; vertical-align: middle; object-fit: contain;" /></a> |

For users without Google Play access, install the apk from the [**latest release**](https://github.com/google-ai-edge/gallery/releases/latest/)


## App Preview

<img width="480" alt="01" src="https://github.com/user-attachments/assets/a809ad78-aef4-4169-91ee-de7213cbb3bd" />
<img width="480" alt="02" src="https://github.com/user-attachments/assets/1effd10d-f45a-4f7b-9435-f50f1bdd36b6" />
<img width="480" alt="03" src="https://github.com/user-attachments/assets/e5089e41-2c18-4fbe-9011-ebe9e5a02044" />
<img width="480" alt="04" src="https://github.com/user-attachments/assets/0f39d3ed-7403-4606-a7c6-b2c7e51ba6c1" />
<img width="480" alt="05" src="https://github.com/user-attachments/assets/8c229e96-b598-4735-9f60-e96907e1d5d5" />
<img width="480" alt="06" src="https://github.com/user-attachments/assets/ac9fb77b-81de-4197-9ed3-f6fe58290b3e" />
<img width="480" alt="07" src="https://github.com/user-attachments/assets/bc86ba07-2eaf-49b1-980f-8a87a85c596f" />
<img width="480" alt="08" src="https://github.com/user-attachments/assets/061564ed-030f-4630-810b-13a7863fce4c" />

## ✨ Core Features

* **Agent Skills**: Transform your LLM from a conversationalist into a proactive assistant. Use the Agent Skills tile to augment model capabilities with tools like Wikipedia for fact-grounding, interactive maps, and rich visual summary cards. You can even load modular skills from a URL or browse community contributions on GitHub Discussions.

* **AI Chat with Thinking Mode**: Engage in fluid, multi-turn conversations and toggle the new Thinking Mode to peek "under the hood." This feature allows you to see the model’s step-by-step reasoning process, which is perfect for understanding complex problem-solving. Note: Thinking Mode currently works with supported models, starting with the Gemma 4 family.

* **Ask Image**: Use multimodal power to identify objects, solve visual puzzles, or get detailed descriptions using your device’s camera or photo gallery.

* **Audio Scribe**: Transcribe and translate voice recordings into text in real-time using high-efficiency on-device language models.

* **Prompt Lab**: A dedicated workspace to test different prompts and single-turn use cases with granular control over model parameters like temperature and top-k.

* **Mobile Actions**: Unlock offline device controls and automated tasks powered entirely by a finetune of FuntionGemma 270m.

* **Tiny Garden**: A fun, experimental mini-game that uses natural language to plant and harvest a virtual garden using a finetune of FunctionGemma 270m.

* **Model Management & Benchmark**: Gallery is a flexible sandbox for a wide variety of open-source models. Easily download models from the list or load your own custom models. Manage your model library effortlessly and run benchmark tests to understand exactly how each model performs on your specific hardware.

* **100% On-Device Privacy**: All model inferences happen directly on your device hardware. No internet is required, ensuring total privacy for your prompts, images, and sensitive data.

## 🏁 Get Started in Minutes!

1. **Check OS Requirement**: Android 12 and up, and iOS 17 and up.
2.  **Download the App:**
    - Install the app from [Google Play](https://play.google.com/store/apps/details?id=com.google.ai.edge.gallery) or [App Store](https://apps.apple.com/us/app/google-ai-edge-gallery/id6749645337).
    - For users without Google Play access: install the apk from the [**latest release**](https://github.com/google-ai-edge/gallery/releases/latest/)
3.  **Install & Explore:** For detailed installation instructions (including for corporate devices) and a full user guide, head over to our [**Project Wiki**](https://github.com/google-ai-edge/gallery/wiki)!

## 🛠️ Technology Highlights

*   **Google AI Edge:** Core APIs and tools for on-device ML.
*   **LiteRT:** Lightweight runtime for optimized model execution.
*   **Hugging Face Integration:** For model discovery and download.

## ⌨️ Development

Check out the [development notes](DEVELOPMENT.md) for instructions about how to build the app locally.

## 🤝 Feedback

This is an **experimental Beta release**, and your input is crucial!

*   🐞 **Found a bug?** [Report it here!](https://github.com/google-ai-edge/gallery/issues/new?assignees=&labels=bug&template=bug_report.md&title=%5BBUG%5D)
*   💡 **Have an idea?** [Suggest a feature!](https://github.com/google-ai-edge/gallery/issues/new?assignees=&labels=enhancement&template=feature_request.md&title=%5BFEATURE%5D)

## 📄 License

Licensed under the Apache License, Version 2.0. See the [LICENSE](LICENSE) file for details.

## 🔗 Useful Links

*   [**Project Wiki (Detailed Guides)**](https://github.com/google-ai-edge/gallery/wiki)
*   [Hugging Face LiteRT Community](https://huggingface.co/litert-community)
*   [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM)
*   [Google AI Edge Documentation](https://ai.google.dev/edge)
