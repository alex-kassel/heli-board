# Roadmap

1. Baseline audit: document project architecture, license, build, test, IME UI entry points, clipboard implementation.
2. Microphone button: short and long press with placeholder behavior; no networking.
3. Settings panel: persistent output language, recipient, polish, and preview toggle.
4. Speech recognition: permission handling, recording lifecycle, transcript preview.
5. AI pipeline: controlled text transformation and translation; user consent and secure network design.
6. Integration: preview, insertion, retry, errors, and sensitive-field behavior.
7. Packaging: CI build and downloadable debug APK; real-device smoke testing.

Each stage should be a small PR with evidence of validation.

## Planned v0.2 — Incoming voice messages

After the initial keyboard MVP is stable, implement audio import, multilingual transcription, full translation, and concise key-point summaries. See [Task 006](tasks/006-incoming-voice-summaries.md). This is not a blocker for v0.1.
