# No-code studio change contract

Intent: let a beginner create and use an actual app without source editing, and make the existing advanced workspace optional.

Allowed scope: add a guided native studio, a validated declarative app model, offline starters, conversational edits, per-project full previews, version restore, portable backups and standalone exports. Improve AI error handling and verify the renderer, persistence, compiler and Android lint. Generated apps run entirely on device; unsupported services must be named as unavailable rather than simulated.

Protected scope: existing project IDs, conversations, encrypted provider credentials, advanced orchestration, CI connectors and PocketForge updater. Keep all advanced tools accessible. Never claim that an exported project is a compiled APK. Repository Autopilot remains governed by AUTOPILOT_DESIGN.md.

Verification: JVM schema/compatibility/serialization/export tests; browser interaction tests for the deterministic renderer with real input persistence and separate project storage; Android unit tests, lint and signed APK assembly. Review phone layouts, large text, keyboard insets and back navigation. Validate the exported Android project compiles. Keep changes on a dedicated branch and open a reviewable PR.
