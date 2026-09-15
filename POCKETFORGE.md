# PocketForge Project Brain

## Product promise
PocketForge is Cursor for people who do not know code: users describe software in normal language and receive a working app without dealing with source files, Git, Gradle, dependencies, or compiler errors.

## MVP surfaces
- Build: natural-language app/change request and change contract.
- Preview: user-facing product preview, not source code.
- Changes: human-readable history.
- Health: plain-English system/build status.
- Publish: APK and eventual store/web publishing.

## Safety rule for coding agents
Before changing code, translate the request into a narrow change contract containing intent, allowed scope, protected scope, and verification steps. Prefer the smallest safe change. Do not modify unrelated behavior. Build and verify before calling a change complete.
