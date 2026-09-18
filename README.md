# Vipo — Your models. Your device. Your conversations.

[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![Engine](https://img.shields.io/badge/Inference-llama.cpp-orange.svg)](https://github.com/ggerganov/llama.cpp)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Offline-brightgreen.svg)](#privacy-guarantee)

**Vipo** is a native Android application engineered for 100% private, local on-device generative AI. It allows you to download, manage, and execute quantized **GGUF** models directly on your smartphone processor without relying on cloud APIs, telemetry, or external servers.

---

## 🔒 Privacy Guarantee

- **No Remote Inference:** Zero API calls to OpenAI, Gemini, Anthropic, or third-party proxies.
- **Local Storage Only:** Conversations, system prompts, and model weights are strictly stored on internal device storage in Room and DataStore.
- **Works in Airplane Mode:** Once models are downloaded, all inference operates completely offline.

---

## 🚀 Key Features

### 1. Local AI & Streaming Inference
- Integrated abstraction over **llama.cpp** with token-by-token streaming.
- Model lifecycle management: dynamic loading, clean context unloading, and native memory reclamation.
- Real-time generation controls: stop mid-generation, regenerate responses, and edit prior queries.

### 2. Large Model Hub (100+ Models & Variants)
- Extensive catalog covering modern open-weight model architectures:
  - **Llama 3 / 3.1 / 3.2** (1B, 3B, 8B)
  - **Qwen 2.5** (0.5B, 1.5B, 3B, 7B) & Qwen 2.5 Coder
  - **Gemma 2** (2B, 9B)
  - **Phi-3.5 / Phi-4**
  - **SmolLM2** (135M, 360M, 1.7B)
  - **DeepSeek-R1 Distill** (1.5B, 7B, 8B)
- Full GGUF quantization selector: `Q2_K`, `Q3_K_M`, `Q4_K_M`, `Q5_K_M`, `Q6_K`, `Q8_0`.

### 3. Smart Device Recommendation
- Automatically assesses device hardware on launch:
  - Total & available RAM
  - Storage headroom
  - CPU core count
- Labels model compatibility: **Excellent**, **Good**, **Usable**, **Slow**, and **Memory Risk**.
- **Auto Select**: One-tap recommendation that picks the optimal model and quantization for your phone.

### 4. Resumable Model Download Manager
- Multi-threaded background downloads with HTTP Range byte-resumption.
- Progress monitoring: percentage, downloaded MB / total MB, transfer speed, and completion estimates.
- State controls: Pause, Resume, Cancel, and Retry.
- Automatic recovery of incomplete `.part` downloads on app launch.
- Import custom `.gguf` files from phone storage or direct download via URL.

### 5. Deep Chat Customization & Performance Stats
- Live token-per-second (`t/s`) speed tracking, time-to-first-token, prompt tokens, and context window utilization.
- Per-conversation system prompt customization.
- Conversation management: Pinning, renaming, duplicating, searching, and deleting.
- AMOLED Pure Black theme with subtle dark surface hierarchy.

---

## 🏗️ Architecture

```
com.example
├── data
│   ├── local        # Room DB, DAOs, Entities, SettingsDataStore
│   └── model        # ModelCatalogItem, DeviceHardwareInfo, GgufMetadata, Stats
├── download         # ModelDownloadManager, DownloadTask, HTTP Range Worker
├── engine           # InferenceEngine, LlamaCppInferenceEngine, GgufParser, LlamaNative
├── repository       # ConversationRepository, ModelRepository, HardwareRepository
├── ui
│   ├── chat         # ChatScreen, ChatViewModel, InputBar, Bubbles
│   ├── hub          # ModelHubScreen, ModelHubViewModel, ModelCard, ImportDialog
│   ├── settings     # SettingsScreen, SettingsViewModel
│   ├── onboarding   # OnboardingScreen (4-page flow)
│   ├── components   # DeviceSpecsCard, PerformancePanel, NavigationDrawerContent
│   └── theme        # VipoTheme, Dark palette, Typography
└── MainActivity.kt  # Compose Navigation Host
```

---

## 🛠️ Building From Source

### Prerequisites
- Android Studio Ladybug (2024.2+) or newer
- Android SDK 35 (compileSdk 35, minSdk 26)
- JDK 17 or 21
- Android NDK (r26+ recommended for native llama.cpp builds)

### Build Commands
```bash
# Clone the repository
git clone https://github.com/vipo-ai/vipo.git
cd vipo

# Build debug APK
./gradlew assembleDebug

# Run unit and Robolectric tests
./gradlew testDebugUnitTest
```

---

## 📄 License
Distributed under the MIT License. See [LICENSE](LICENSE) for details.
