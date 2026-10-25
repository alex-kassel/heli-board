# Task 003 — Persistent voice settings

Prerequisite: Task 002 complete.

Implement settings through existing HeliBoard persistence conventions: output RU/DE/EN, plain versus AI mode, recipient (unchanged/friend/colleague/manager/client), polish (minimal/balanced/strong), preview toggle default ON. Persist across keyboard sessions and apps. Show current mode clearly.

Acceptance: changes survive IME restart; default behavior is safe; tests cover serialization/defaults; no unrelated settings regressions.
