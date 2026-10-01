# On-device Gemma 4 Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Describe image / Describe screen / follow-ups run on Gemma 4 on the phone, with no API key.

**Architecture:** `LocalGemmaRequestPerformer` extends `GeminiRestRequestPerformer`. When the provider is "on device" it parses the Gemini-style request JSON, runs it through a `LocalLlm` (LiteRT-LM) and returns a `GeminiResponse`. `GeminiRestEndpoint`, prompts, parsing and UI are untouched. `isKeylessInitialized()` reports whether a model is installed.

**Tech Stack:** Kotlin, LiteRT-LM `litertlm-android:0.17.1`, HttpURLConnection, JUnit4.

**Spec:** `docs/superpowers/specs/2026-10-01-on-device-gemma-design.md`

## Global Constraints
- minSdk 26; arm64-v8a only for on-device (the library ships no armeabi-v7a).
- Model files: `gemma-4-E2B-it.litertlm` (2.59 GB, sha256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`), `gemma-4-E4B-it.litertlm` (3.66 GB, sha256 `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0`), from `huggingface.co/litert-community/<repo>/resolve/main/<file>`.
- Fork conventions: own package, `Copyright 2026 Backtalk contributors` header, own `strings_*.xml`, README section.
- Default provider stays Gemini API; nothing changes unless the user switches.

## Tasks
1. **Model catalog + downloader + store** (`actor/gemini/local/LocalModel.kt`, `ModelDownloader.kt`, `LocalModelStore.kt`) with unit tests: resume, hash mismatch, size mismatch, cancel, import.
2. **Runner** (`actor/gemini/LocalGemmaRunner.kt`) with unit tests against a fake `LocalLlm`: request parsing, fence stripping for JSON mode, error and cancel mapping, one request at a time.
3. **LiteRT-LM binding** (`local/LiteRtLmLocalLlm.kt`, gradle dependency, manifest native-library entries): lazy engine, idle unload, cancel.
4. **Wiring**: `LocalGemmaRequestPerformer`, provider pref, `TalkBackService` swap, device gating.
5. **Settings UI** (`OnDeviceAiFragment`, strings, preferences.xml): provider choice, model choice, download/cancel/delete/import with progress, RAM/ABI gating.
6. **Docs + build**: README section, `./gradlew testPhoneDebugUnitTest assemblePhoneDebug`.
7. **Manual device checklist** (needs the phone): latency, memory, quality, GPU vs CPU.
