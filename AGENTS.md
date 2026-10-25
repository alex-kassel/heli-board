# Agent instructions — AI Voice Keyboard

This repository is a fork of HeliBoard. Read the upstream README, CONTRIBUTING.md, AI_USAGE.md, licenses, and existing implementation before editing.

## Product goal
Keep HeliBoard a usable Android keyboard. Add one voice button in the keyboard toolbar: tap to start/stop dictation, long-press to configure a persistent mode. Dictation may be in any language; output language is explicitly selected (Russian, German, English). Modes: unmodified transcription, and AI rewriting for friend, colleague, manager, client; polish minimal, balanced, strong. Insert the reviewed output into the active editor via InputConnection. Keep clipboard history and ordinary keyboard behavior.

## Working rules
- First inspect the actual project architecture and establish a baseline build. Do not assume Kotlin-only or Compose architecture.
- Implement one small task at a time, on a feature branch, with tests and a PR. Never push directly to main.
- Preserve existing features, layouts, multilingual input, clipboard history, accessibility, and privacy defaults.
- Avoid large refactors, speculative abstractions, new infrastructure, and unrelated formatting changes.
- Distinguish verified facts, assumptions, open questions, and decisions in documentation.
- For each task report files changed, build/test commands and outcomes, limitations, and manual Android validation steps.
- Do not silently alter dictated meaning, invent commitments or dates, or send messages automatically.
- Never transmit keyboard input except for an explicit user-initiated voice/AI operation. Never log dictated text, clipboard content, or credentials.
- Never commit API keys. Investigate a secure API architecture before implementing cloud calls; embedding a reusable secret in an APK is not secure.
- Check Android microphone permission, network permission, audio lifecycle, IME lifecycle, privacy indicators, offline fallback, and behavior in password/sensitive fields.
- Preserve GPL-3.0 obligations and existing notices.

## Execution order
1. docs/tasks/001-project-audit.md
2. docs/tasks/002-voice-button.md
3. docs/tasks/003-voice-settings.md
4. docs/tasks/004-speech-recognition.md
5. docs/tasks/005-ai-processing.md

Do not begin implementation before the audit is complete and recorded.
