---
name: android-project-scaffold
description: "Scaffold or extend the MMO Catch Recording Android app structure: Jetpack Compose app skeleton, feature packages, Core layers (networking, offline persistence/sync, design system), Gradle setup, product flavors/config, ADRs and README. Use when creating the initial project, adding a new feature module, or establishing conventions."
argument-hint: "e.g. 'scaffold the initial app' or 'add a Catch Recording feature module'"
user-invocable: false
---

# Android project scaffold

Use this skill to create a clean, DEFRA-aligned, testable Jetpack Compose project structure — or to add a
new feature module that matches existing conventions.

## When to use
- First-time project setup (no boilerplate yet).
- Adding a new feature module (screen + view model + models + tests).
- Establishing/refreshing shared Core layers and the design system.

## Before you scaffold (Confirm tech stack → Read → Clarify)
0. **Tech-stack confirmation gate (mandatory, copilot-instructions §5.1).** The §5 tech-stack decisions are
   **tentative** until the Android developer confirms them. Before scaffolding any code, check the
   **Android Developer** agent's `Tech-stack confirmation status` block: if it reads `❌ Not yet confirmed`,
   **stop and prompt the developer** to confirm/amend the app stack, record architectural choices as ADRs,
   then flip that agent file's status to `✅ Confirmed …`. Do not scaffold until this gate is passed.
1. **Read** the repo. If a project/architecture already exists, follow it — do **not** restructure.
2. **Clarify** with the developer if no pattern exists:
   - Architecture pattern (recommend **MVVM + unidirectional data flow** with **Hilt** DI).
   - Persistence choice for offline (recommend **Room** + **WorkManager** for the sync queue, **DataStore**
     for preferences).
   - `applicationId` scheme and environments (`dev`/`test`/`prod` via product flavors).
   Record decisions as ADRs in `docs/adr/`.

## Procedure
1. Create the folder/package structure (see `references/project-structure.md`).
2. Set up the **Gradle (Kotlin DSL)** build with a **version catalog** (`gradle/libs.versions.toml`); pin
   versions. Add the Compose BOM, Hilt, Room, WorkManager, Retrofit, Coroutines, and test libraries.
3. Add the Application class (`@HiltAndroidApp`) and a single Activity host with a Compose `setContent`
   entry + Navigation-Compose graph.
4. Add **product flavors** (`dev`/`test`/`prod`) with `applicationIdSuffix`, `BuildConfig`/resource values
   and endpoints per environment; inject secrets at build time from CI (never commit them). Add a
   `network_security_config.xml` that disables cleartext.
5. Stub the Core layers: `network` (Retrofit API + DTOs + error mapping), `persistence` (Room + `SyncWorker`
   + offline mutation queue + migrations), `model`, `designsystem` (accessible reusable components, Material
   3 theme/typography).
6. Add `src/test/` (JVM unit) and `src/androidTest/` (Compose UI + Espresso + accessibility) source sets
   mirroring the source tree, with at least one passing sample test. Wire **Kover** coverage.
7. Add/refresh the **README** to DEFRA
   [README standards](https://defra.github.io/software-development-standards/standards/readme_standards/),
   plus a solution overview and architecture diagram.
8. Wire **ktlint** + **detekt** + Android Lint config if the team uses it.

## Standards to honour
- [kotlin-compose instructions](../../instructions/kotlin-compose.instructions.md) (patterns, layout, offline-first)
- [accessibility instructions](../../instructions/accessibility.instructions.md) (design-system components must be accessible)
- [security instructions](../../instructions/security.instructions.md) (Keystore, TLS/network security config, no secrets)

## Validate
- Project builds: `./gradlew assembleDebug`.
- Sample tests pass: `./gradlew testDebugUnitTest` (and `connectedDebugAndroidTest` on an emulator) or
  `fastlane test`.
- Structure matches `references/project-structure.md` and any ADRs.
