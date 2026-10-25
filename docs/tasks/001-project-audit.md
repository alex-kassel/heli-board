# Task 001 — Audit the fork and baseline build

## Goal
Establish the actual project architecture and a reproducible baseline before making feature changes.

## Steps
1. Inspect README, CONTRIBUTING.md, AI_USAGE.md, license files, Gradle configuration, Android manifest, source tree.
2. Identify the IME service, keyboard toolbar, gesture/long-press handling, settings persistence, clipboard history, and text insertion paths, with file paths and relevant symbols.
3. Determine supported Android versions, build variants, dependencies, and available tests.
4. Run a clean debug build and relevant tests where the local environment permits. Record exact commands and results. Do not claim success without running them.
5. Evaluate where microphone permission, speech recognition, network permission, and AI processing could fit with minimal changes.
6. Record privacy and license considerations, and unresolved design decisions.

## Deliverables
- docs/AUDIT.md with evidence-backed findings and file references.
- Build/test results and blockers.
- A minimal implementation plan for Task 002.

## Acceptance
No production code changed; architecture references are grounded in actual files; baseline build status is explicit; next integration points are concrete.
