# AI Voice Keyboard — product specification

## Core interaction
- One microphone control integrated into HeliBoard's existing UI.
- Short tap: start dictation; next tap: stop and process.
- Long press: open a mode configuration panel. Selection persists across apps and messages until changed.
- Output language: RU, DE, EN, regardless of spoken language (including mixed-language speech, subject to recognition capabilities).
- Voice modes: plain transcription; AI-assisted text.
- Recipient/tone: unchanged, friend, colleague, manager, client.
- Polish: minimal (grammar/punctuation), balanced (natural phrasing), strong (professional rewrite).
- Preview before insertion is configurable; default ON. Allow cancel, retry, and switching style without recording again.
- Insert into the current editor, never auto-send.

## Existing keyboard features
Retain multilingual layouts, dictionaries, autocomplete, clipboard history and pinning, and other existing HeliBoard features. Desired clipboard limit: 20 recent items plus pinned; validate existing implementation before changing it.

## Safety and privacy
- Do not capture or transmit ordinary typing or clipboard data.
- Make cloud processing and its implications clear before enabling.
- Exclude password and sensitive editor fields from AI actions.
- Avoid logging voice, transcripts, generated text, and credentials.
- Plain transcription and AI transformation must remain distinguishable.
- Do not fabricate facts, dates, promises, or commitments when rewriting.

## Out of scope for first release
Gboard integration, floating overlays, automatic sending, server-side conversation history, replacing the entire keyboard UI.

## Planned follow-up: incoming voice message summaries (v0.2)

Import audio from Android sharing where supported or via a file picker. Transcribe and summarize key decisions, action items, deadlines, and questions in a selected output language (RU/DE/EN), independent of the spoken language. Allow viewing the full transcript for verification. Do not auto-send replies. See [Task 006](tasks/006-incoming-voice-summaries.md).
