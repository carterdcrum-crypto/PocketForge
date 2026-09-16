# PocketForge Project Brain

## Product promise
PocketForge is Cursor for people who do not know code: users describe software in normal language and receive a working app without dealing with source files, Git, Gradle, dependencies, or compiler errors.

## MVP surfaces
- Build: natural-language app/change request and change contract.
- Preview: user-facing product preview, not source code.
- Changes: human-readable history.
- Health: plain-English system/build status.
- Publish: APK and eventual store/web publishing.

## Monetization requirements
- PocketForge must remain genuinely useful on the free tier. Free users should be able to build and experience the core product rather than receiving a crippled demo.
- Monetization must be simple, transparent, and affordable.
- Before a defined founder cutoff date, eligible users may purchase a relatively low-cost one-time Founders Lifetime entitlement. The purchase window ends on the cutoff date, but users who already bought it keep their lifetime entitlement.
- After the founder cutoff date, new paid users join through a recurring monthly membership. Existing Founders Lifetime users are grandfathered and are not converted to monthly billing.
- Paid benefits must provide clear continuing value and must never rely on dark patterns or misleading billing language.
- Entitlements and purchase validation must be authoritative on a backend, not only in the APK.

## Play Store release requirements
- Remove the current self-update / APK update button and any sideload-oriented updater flow before the production Google Play release.
- Production releases distributed through Google Play must use Play-supported update and billing paths.
- Keep internal/beta update tooling separate from the Play Store production build when useful for development.

## Security and defensibility requirements
- Treat the Android APK as an untrusted client. Never rely on client-side secrecy for API keys, premium entitlements, proprietary prompts, routing rules, or valuable business logic.
- Keep high-value proprietary orchestration, entitlement checks, abuse controls, model-routing policy, and differentiating features server-side whenever practical.
- Do not ship provider master keys or backend secrets inside the APK.
- Validate paid entitlements server-side and use short-lived authenticated sessions/tokens for protected backend operations.
- Use release obfuscation/minification, resource shrinking, tamper checks, signing verification, Play Integrity where appropriate, rate limiting, server-side authorization, replay protection, and telemetry for abuse detection.
- Design cloned or modified APKs to have little value without valid server authorization.
- Security work must be layered and realistic: make reverse engineering expensive and low-value, but never claim an Android app can be made literally impossible to reverse engineer.
- Build product defensibility through server-side capabilities, durable project memory, verified build pipelines, integrations, proprietary workflow/orchestration, and accumulated user/project state rather than obscurity alone.

## Safety rule for coding agents
Before changing code, translate the request into a narrow change contract containing intent, allowed scope, protected scope, and verification steps. Prefer the smallest safe change. Do not modify unrelated behavior. Build and verify before calling a change complete.
