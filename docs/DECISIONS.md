# Architecture decisions log

## D-001 — Fork HeliBoard
Status: accepted. Preserve its existing keyboard functionality and GPL-3.0 notices.

## D-002 — Single voice control
Status: accepted. Short tap controls dictation; long press opens persistent mode configuration.

## D-003 — Output language independent of spoken language
Status: accepted product requirement. Technical feasibility and recognition engine selection remain open.

## D-004 — Cloud AI provider and credential management
Status: accepted.
1. No hardcoded or embedded API keys in the APK.
2. Hybrid OpenAI-compatible architecture: configurable API endpoint (defaulting to OpenAI `/v1` or custom proxy e.g. `ab-box.de`), API key, and model in keyboard settings.
3. Supports direct BYOK (Bring-Your-Own-Key for OpenAI, Groq, OpenRouter, DeepSeek) as well as self-hosted backend proxies.
4. Credentials stored in private app SharedPreferences (isolated to app sandbox, never committed, never logged).
5. Explicit user Consent Dialog required before any network request is transmitted.
6. Zero typing or clipboard data exfiltration; sensitive/password fields strictly protected.

## D-005 — Clipboard retention
Status: accepted. Retain up to 20 recent unpinned clipboard items plus all user-pinned items. Older unpinned items beyond 20 are automatically pruned in `ClipboardDao.clearOldClips()`.

## D-006 — Microphone button placement
Status: accepted. Place the microphone button on the far right (top-right) of the keyboard toolbar, aligned with Gboard ergonomics for natural right-hand thumb access.
