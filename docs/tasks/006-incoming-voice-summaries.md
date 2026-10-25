# Future feature — Incoming voice messages and summaries

Status: planned for v0.2; not part of v0.1 acceptance criteria.

## User problem
A user receives a long voice message (e.g. three minutes) but cannot listen immediately. They want the important information as readable text in a selected output language, regardless of the audio's spoken language.

## User experience
1. Share an audio file from a messenger or select a local audio file in the app.
2. Select output language: Russian, German, or English; independently of source language.
3. Process the file and display a concise, faithful summary with key points.
4. Optionally expand to a full transcript and/or full translation.
5. Copy or share the resulting text. Never send a reply automatically.

## Summary requirements
- Prioritize decisions, requested actions, deadlines, times, names, dates, amounts, and questions requiring a response.
- Preserve uncertainties and distinguish what was said from any interpretation.
- Never invent details or convert suggestions into commitments.
- Make the original transcript accessible so the user can verify the summary.
- Handle mixed-language speech when supported by the chosen recognition engine.
- Show useful errors for unsupported audio, recognition failures, and network failures.

## Technical notes
- Implement as an Android Activity/screen separate from the IME, with an ACTION_SEND audio/* entry point where supported and a local file picker fallback.
- Sharing voice notes depends on capabilities of the originating messenger; do not promise direct import from every app.
- Reuse transcription, language selection, and processing abstractions from the keyboard where appropriate.
- Do not couple long-running audio processing to the IME lifecycle.
- Consider audio size limits, long-running jobs, cancellation, temporary-file deletion, and progress indicators.
- Require informed consent before uploading audio/transcripts to a cloud provider. Avoid recording or logging message contents. Respect Android scoped storage and URI permissions.
- Do not embed shared provider secrets in an APK.

## Acceptance criteria for v0.2
- User can import a supported audio file, choose RU/DE/EN, and receive a concise summary.
- Full transcript is available for verification.
- Dates, numbers, and action items are faithfully represented in test fixtures.
- Cancellation and failure cases are handled without data loss or unintended uploads.
- Manual testing covers at least one messenger sharing path and file picker fallback.

## Dependencies
Complete and validate v0.1 voice recognition and text processing first. Engine/provider selection is intentionally deferred until project audit and v0.1 implementation.
