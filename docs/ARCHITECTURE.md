# Architecture — provisional, pending code audit

Use the existing HeliBoard architecture, lifecycle, settings patterns, and UI components. Do not introduce a new framework without evidence.

Expected responsibilities (not prescribed class names):
1. IME integration: microphone UI, press/long-press handling, InputConnection insertion.
2. Voice session controller: permission, start/stop, lifecycle, cancellation, error handling.
3. Speech recognition adapter: multilingual detection/recognition, availability, fallback.
4. Processing engine: target language, tone, polish, plain transcription versus AI processing.
5. Persistent configuration: selected mode, output language, preview preference.
6. Preview UI: compare original and result, edit/cancel/insert.
7. Optional cloud gateway: consent, request boundaries, credential protection, timeouts.

Architecture decisions to investigate:
- Whether Android SpeechRecognizer supports required multilingual behavior on target devices.
- Whether cloud STT is necessary for mixed-language speech.
- How to handle API authentication without embedding long-lived secrets in the APK.
- Whether to use a minimal backend proxy for production.
- How HeliBoard's existing settings and toolbar are implemented.
- How the existing clipboard history behaves and is configured.

No implementation approach is approved until audit findings are documented.
