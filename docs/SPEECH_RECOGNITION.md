# Speech Recognition Architecture & Analysis (Task 004)

## 1. Overview
Task 004 integrates speech recognition directly into HeliBoard's keyboard lifecycle, fulfilling Decision `D-002` (single voice control) and preparing the transcription pipeline for AI processing (`Task 005`).

## 2. Architecture & Components

```
                +---------------------------------------+
                |     Top-Right Microphone Button       |
                |   (ToolbarKey.VOICE / KeyCode -102)   |
                +---------------------------------------+
                                   |
                         toggleVoiceInput()
                                   v
                +---------------------------------------+
                |         VoiceInputController          |
                |   - Sensitive field safety guard      |
                |   - Runtime RECORD_AUDIO checker      |
                |   - Recognition lifecycle manager     |
                +---------------------------------------+
                     |                     |
     [Permission Missing]           [Adapter Call]
                     v                     v
   +---------------------------+   +-------------------------------+
   |  VoicePermissionActivity  |   |    SpeechRecognitionAdapter   |
   | (transparent IME helper)  |   | (AndroidSpeechRecognitionAd.) |
   +---------------------------+   +-------------------------------+
                                                   |
                                                   v
                                   +-------------------------------+
                                   |  Android SpeechRecognizer     |
                                   | (system / on-device service)  |
                                   +-------------------------------+
                                                   |
                                         Results / Error
                                                   v
                +---------------------------------------+
                |        VoiceInputController           |
                |  - isPreviewEnabled ? Preview Dialog  |
                |  - onTextInput() -> Editor Insertion  |
                |  - Zero Auto-Send                     |
                +---------------------------------------+
```

### Components
1. **`VoicePermissionActivity`**:
   - `InputMethodService` is a background service and cannot directly receive `onRequestPermissionsResult`.
   - A lightweight transparent activity (`VoicePermissionActivity`) is launched when `RECORD_AUDIO` is missing, prompts the user, notifies `VoiceInputController.onPermissionResult()`, and cleanly finishes with zero animation.
2. **`SpeechRecognitionAdapter`**:
   - Interface abstracting speech-to-text engines, decoupling IME logic from Android platform details.
   - Enables mock injection in unit tests and seamless extension for future custom Whisper / local Vosk engines.
3. **`AndroidSpeechRecognitionAdapter`**:
   - Standard Android implementation using `android.speech.SpeechRecognizer`.
   - Maps platform errors (`ERROR_AUDIO`, `ERROR_NETWORK`, `ERROR_INSUFFICIENT_PERMISSIONS`, `ERROR_NO_MATCH`, etc.) to clear human-readable messages.
   - Enforces thread safety by dispatching lifecycle operations on `Looper.getMainLooper()`.
4. **Lifecycle Hooks in `LatinIME`**:
   - `cleanupInternalStateForFinishInput()` -> cancels active recognition when keyboard is closed or app switches.
   - `onDestroy()` -> full teardown and unbinding of speech recognition service.

---

## 3. Multilingual & Mixed-Language Limitations

### Single-Locale Model Constraints
- Android's native `SpeechRecognizer` processes audio against a specified language tag (`EXTRA_LANGUAGE`, e.g., `ru-RU`, `de-DE`, `en-US`).
- When a user speaks a mixed-language sentence (e.g., German syntax interspersed with Russian phrases or English technical jargon):
  - Standard speech recognizers attempt to force phonemes into the selected target language's phonetic dictionary.
  - This results in phonetic corruptions, misspellings, or dropped phrases.
- **Remediation Strategy**:
  - The voice settings panel allows explicitly targeting the intended output language.
  - In Task 005 (AI Processing), LLM transformation receives the transcribed phonetic text and normalizes multilingual terminology into the desired recipient tone and target language.

---

## 4. Offline vs Online Behavior

1. **On-Device (Offline) Recognition**:
   - Supported on Android devices with installed offline language packages (e.g., Google Speech Services Offline Packs).
   - HeliBoard passes `EXTRA_PREFER_OFFLINE = true` where available.
   - Offline transcription provides zero-latency start, zero network telemetry, and full air-gapped privacy.
2. **Online Fallback**:
   - When offline models are not installed on the host device, the underlying Android speech engine requires an active network connection.
   - If network connectivity is unavailable, the adapter captures `SpeechRecognizer.ERROR_NETWORK` and informs the user via toast.
3. **AOSP / De-Googled ROM Compatibility**:
   - On custom ROMs lacking a speech service (e.g., LineageOS without GApps/MicroG), `SpeechRecognizer.isRecognitionAvailable(context)` returns `false`.
   - HeliBoard detects this condition immediately, alerting the user to install a compatible speech provider without crashing.

---

## 5. Privacy & Safety Guarantees

- **Sensitive & Password Fields**:
  - Voice recording is strictly blocked in all password input types (`TYPE_TEXT_VARIATION_PASSWORD`, `TYPE_TEXT_VARIATION_VISIBLE_PASSWORD`, `TYPE_TEXT_VARIATION_WEB_PASSWORD`).
- **Zero Auto-Send**:
  - Transcriptions are only inserted into the active editor via `InputConnection.commitText()` (`LatinIME.onTextInput()`).
  - No action key (e.g. Enter / Send) is automatically dispatched.
- **Preview & Inspection**:
  - When `isPreviewEnabled` is `true` (default), recognized text is presented in an attached preview dialog with explicit **Insert** and **Discard** actions.
- **Zero Audio Logging**:
  - Audio buffers and raw voice waveforms are never persisted to disk or written to diagnostic logs.
