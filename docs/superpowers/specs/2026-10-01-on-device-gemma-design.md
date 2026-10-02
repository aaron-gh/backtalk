# On-device Gemma 4 for Describe image / Describe screen

## Problem
Describe image, Describe screen and follow-up questions need a Gemini API key, and the free tier is too small. We want a no-key, no-quota option that works offline after a one-time model download.

## Decision
Run **Gemma 4 E2B** (optionally **E4B**) on the phone with **LiteRT-LM** (`com.google.ai.edge.litertlm:litertlm-android`, Kotlin API). It supports image input.

Why Gemma 4 rather than Gemma 3n: official `litert-community` repos, ungated (no login/token), Apache-2.0, smaller (2.59 GB), less RAM (about 1.7 GB CPU / 0.7 GB GPU on an S26 Ultra per the model card), faster, and LiteRT-LM is the maintained runtime (MediaPipe LLM Inference is the legacy path).

| Model | File | Size | SHA-256 |
|---|---|---|---|
| E2B (default) | `litert-community/gemma-4-E2B-it-litert-lm` / `gemma-4-E2B-it.litertlm` | 2.59 GB | `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c` |
| E4B (optional, 8 GB+ RAM) | `litert-community/gemma-4-E4B-it-litert-lm` / `gemma-4-E4B-it.litertlm` | 3.66 GB | `0b2a8980ce155fd97673d8e820b4d29d9c7d99b8fa6806f425d969b145bd52e0` |

Download URL: `https://huggingface.co/<repo>/resolve/main/<file>` (anonymous, verified 200).

## Design
Existing seam: `TalkBackService` builds `GeminiActor` with a `GeminiActor.GeminiEndpoint`. UI (`ImageQnAChatController`, `ScreenQAChatController`, bottom sheets) stays unchanged.

1. **`LocalGemmaEndpoint`** (new, in `actor/gemini/`) implements `GeminiEndpoint`, including the `GeminiCommand` overload (CommonRequest, ScreenOverview, ScreenQuery). It reuses the prompts and response parsing that `ScreenOverviewRequester` uses for the REST path, so output shape is identical.
2. **`LocalModelManager`** (new): owns model file state (absent / downloading / verifying / ready / failed), the download, the hash check and the storage path (`filesDir/models/`).
3. **Provider setting** in Gemini settings: `Gemini API key` / `On-device Gemma` / `Off`. `TalkBackService` selects the endpoint at construction and on preference change. Default stays as today.
4. **Download**: WorkManager or foreground service, HTTP Range resume, progress notification, free-space check (file size x 1.2), cellular warning, SHA-256 verified before the atomic rename into place. A bad hash deletes the file and reports an error.
5. **File picker fallback**: "Use a model file from storage" for sideloading. The picked file is copied in and hash-checked against the table.
6. **Inference**: `Engine` created lazily on first request on a background dispatcher, CPU backend by default with GPU as an optional setting (needs the two `uses-native-library` manifest entries), `cacheDir` set. Engine is closed after an idle timeout (default 2 minutes) to release RAM. One request at a time; a new request cancels the pending one (matches `cancelCommand` / `hasPendingTransaction`). The screenshot is sent as an image part. Follow-up questions reuse one `Conversation` per result sheet and close it when the sheet closes.
7. **Errors**: map to existing `ErrorReason` / `FinishReason` (`UNSUPPORTED` when no model or unsupported device, `ERROR_RESPONSE` on load or inference failure, `JOB_CANCELLED` on cancel). Low-memory kill or load failure surfaces a spoken error, never a crash.
8. **Gating**: option shown only on arm64; E2B needs about 6 GB RAM, E4B about 8 GB (checked with `ActivityManager.MemoryInfo.totalMem`). Disabled rows say why, for TalkBack users.
9. **Privacy text**: README and the opt-in dialog state that in on-device mode images never leave the phone. Update the README "Image descriptions with Gemini" section.

## Out of scope
Gemini Nano/AICore changes, voice commands, vendor-specific NPU model files, other model families, auto-updating models.

## Testing
- Unit: `LocalModelManager` (resume, hash mismatch, low space, cancel) with a fake HTTP server; endpoint request/response mapping with a fake `Engine` wrapper interface.
- Manual on device: download, airplane-mode describe image, describe screen plus follow-up, cancel mid-answer, idle unload, low-RAM gating, TalkBack announcements for progress and errors.
- Measure: first-token latency and RAM for E2B CPU vs GPU, to choose the default backend.

## Risks
- LiteRT-LM API is pre-1.0 (0.17.1 at time of writing); pin the version.
- Answer quality of E2B on dense screens is unknown until tested; E4B is the escape hatch.
- Adds roughly 10-20 MB of native libs per ABI to the APK (to be measured).
- Download is 2.6-3.7 GB, so the settings screen must be clear about size and Wi-Fi.
