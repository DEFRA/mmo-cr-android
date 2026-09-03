# MMO Catch Recording — Android App (Project Guidelines)

This repository holds the source code for the **MMO Catch Recording** native Android application for
the Marine Management Organisation, part of the Department for Environment, Food and Rural
Affairs (**DEFRA**).

These guidelines apply to **every** chat request in this workspace and are inherited by custom
agents (including [Android Developer](.github/agents/android-developer.agent.md),
[Android Planner](.github/agents/android-planner.agent.md) and
[Android DevOps](.github/agents/android-devops.agent.md)).

---

## 1. Standards precedence (highest wins)

When guidance conflicts, follow this order:

1. **DEFRA Software Development Standards** (mandatory) — https://defra.github.io/software-development-standards/
2. **DEFRA Digital Service Manual** — https://digital.defra.gov.uk/service-manual
3. **GOV.UK Service Standard & Service Manual (GDS)** — https://www.gov.uk/service-manual
4. **Google/Android** — Material Design 3, Android app quality & architecture guidance, Google Play policies
5. **Community best practice** — OWASP MASVS, widely-adopted Jetpack Compose patterns

> **DEFRA takes precedence over GDS. GDS takes precedence over Google/Android/community guidance.**
> Any deviation from a DEFRA standard MUST be raised as a formal exception through DEFRA's
> architectural governance (Delivery Architecture team: `delivery.architecture@defra.gov.uk`).

## 2. ⚠️ Known requirement gap — native Android vs DEFRA mobile standard

DEFRA's [Mobile application standards](https://defra.github.io/software-development-standards/standards/mobile_app_standards/)
define a **preference hierarchy** (off-the-shelf → Power App → Progressive Web App → cross-platform
Flutter/React Native) and state **"Don't build native apps"** except as a governed exception.

This repository builds a **native Android (Kotlin/Jetpack Compose)** app. Agents MUST:
- Treat the native decision as an **agreed architectural exception** and remind the team to record it
  as an Architecture Decision Record (ADR) and log it with the Delivery Architecture team if not
  already done.
- Still enforce every DEFRA mobile requirement that applies to native apps (offline-first, encryption
  in transit, error logging, data-at-rest protection, device/beta testing, MDM/Play Store
  distribution). See [security](.github/instructions/security.instructions.md) and
  [ci-cd](.github/instructions/ci-cd.instructions.md) instructions.

## 3. Mandatory DEFRA constraints (apply to all work)

- **Offline-first.** Assume the app must remain useful with no connectivity; degrade gracefully and sync
  when back online. Field workers operate in remote coastal/rural locations.
- **Encrypt all traffic** (HTTPS/TLS). Never send data over plain HTTP. Enforce a network security config
  that blocks cleartext traffic.
- **Protect data at rest** on the device (Android Keystore for keys/secrets, encrypted storage for
  sensitive data).
- **Log errors** so a user can share diagnostics for support; support a debug logging level.
- **Code in the open** in the [DEFRA GitHub org](https://github.com/DEFRA); analyse quality/coverage in
  [DEFRA SonarCloud](https://sonarcloud.io/organizations/defra).
- **Never commit secrets.** Follow DEFRA's
  [credential exposure](https://defra.github.io/software-development-standards/processes/credential_exposure/) process if a secret leaks.
- **Accessibility is a legal requirement:** meet **WCAG 2.2 level AA** and work with common assistive
  technologies (see [accessibility](.github/instructions/accessibility.instructions.md)).
- **Secure by Design** (https://www.security.gov.uk/guidance/secure-by-design/principles/).
- Maintain a README to DEFRA
  [README standards](https://defra.github.io/software-development-standards/standards/readme_standards/),
  plus a solution overview, ADRs and architecture diagrams.

## 4. The working framework (Triage → Read → Research → Clarify → Plan → Approval → Implement → Test → Iterate → Summarise)

This section is the **single source of truth** for the working loop. Custom agents reference it and
**must not restate or fork it**. The guiding principle is **match effort to risk**: do the least work that
still delivers the change safely and to standard. Do not run heavy planning, research or review on work
that does not need it.

**Triage first — pick one of three gears by size and risk:**

- **Trivial** (typo, copy/comment/doc tweak, a small localised change with no impact on architecture,
  networking, persistence/sync, auth, security or accessibility): skip the planner, research and review. Do
  a light **Read → Implement → Test → Summarise**, and research only the one point that is genuinely
  uncertain.
- **Standard** (a normal feature, screen or fix with **no** new architecture, auth, persistence/sync
  strategy or security surface): use a **lightweight inline plan** (a short Objective · Plan · Files ·
  Validation · Risks note — no heavyweight planning agent), get approval, then implement and test. Run a
  **single** risk-scoped research pass **only if** something is genuinely uncertain. **Code review is not
  run by default** (see below).
- **Complex** (new architecture, networking/persistence/sync strategy, external integration, auth, a
  security surface, or multi-item delivery): run the full loop with the designated planning agent and its
  full plan.

**Manual override (the user can force a gear).** Automatic triage is only the default. When the user
explicitly asks for a specific path — e.g. _"treat this as trivial"_, _"just do a standard/lightweight
plan"_, _"force the full complex plan"_, _"skip the planner"_, or _"run a full plan and review"_ — that
instruction **wins over the automatic classification**. Always honour a request for **more** rigour. When
the user asks for **less** rigour than the risk warrants, comply but **briefly flag the risk first**, and
**never drop the approval gate, WCAG 2.2 AA or security** for a change that genuinely touches architecture,
auth, persistence/sync, data correctness or a security surface — those safety gates hold regardless of a
downgrade request.

The loop (Standard and Complex; Trivial uses the light path above):

1. **Read** — Read the relevant files/config in the repo for context before acting. Never assume; verify.
2. **Research (single pass, risk-scoped)** — When something is genuinely uncertain — an unfamiliar or
   version-sensitive API, security, accessibility or DEFRA/GDS/Android policy — do **one** thorough,
   risk-scoped internet research pass in the open and validate findings against Google/Android, DEFRA/GDS and
   framework (Kotlin/Jetpack Compose) guidance so advice reflects current APIs and policy. Cite sources. **Do not run
   a second, separate "validation" research round** — the plan is validated against these same cited
   sources.
3. **Clarify** — Ask the user targeted questions whenever requirements are ambiguous or missing. Surface
   requirement gaps explicitly with suggested fixes. Do not guess at intent.
4. **Plan** — For **Complex** work, delegate planning to the designated planning agent (for Android
   implementation, [Android Planner](.github/agents/android-planner.agent.md)), which returns a complete plan with
   its research already cited. For **Standard** work, produce the lightweight inline plan directly — no
   separate planning agent. Either way, **check** the plan's risky/version-sensitive steps are covered and
   cited; only send a targeted revision back if a genuine gap is found (do not re-research what is already
   cited).
5. **Approval** — Present the plan to the user and obtain explicit approval before implementation. If
   changes are requested, update the plan and re-present. **Cap the plan → approve → implement cycle at 3
   iterations**; if still unresolved, stop and surface the blocker to the user instead of looping.
6. **Implement** — Deliver one task at a time (or parallel independent tasks) from the approved plan. Stay
   focused on the requested outcome; do not scope-creep or refactor unrelated code. **When a change
   establishes or alters architecture** (a new architecture pattern, persistence/sync strategy, external
   integration, auth) — or when starting development on this repo while it still has no app code — create
   the required ADR(s) first under `docs/adr/` (architecture pattern, offline persistence choice, and the
   native-app exception), then build against them. When a screen is built from a Figma design, the design
   is the visual/component authority: build it as designed, **record any deviation from the app
   DesignSystem / Material 3 / GOV.UK content patterns**, and keep **WCAG 2.2 AA and security as
   non-negotiable overrides** that still win over the design (see
   [figma-design instructions](.github/instructions/figma-design.instructions.md)).
7. **Test / Validate** — Build, run unit/UI/accessibility tests, lint, check errors, and confirm each task
   works before moving on.
8. **Iterate** — Refine until the user is satisfied with each task.
9. **Summarise** — End with a detailed **executive summary** of what changed, why, how it was validated,
   any GDS/Material 3/DesignSystem deviations recorded, and any follow-ups or risks.

**Code review is optional and on-request.** A full code review is **not** part of the default loop. Run it
only when the user asks for one. At the end of implementation, if no review has been run, **offer** one
(a single Yes/No question); invoke the reviewer only on an explicit Yes.

## 5. Tech stack (TENTATIVE — proposed defaults, pending developer confirmation)

> ⚠️ **Every decision in this section is a _tentative proposed default_, not a fixed decision.** Nothing
> here is settled until the **Android developer explicitly confirms it** (see §5.1, the tech-stack
> confirmation gate). Treat these as sensible starting recommendations to speed up the conversation — not
> as licence to start building against them. They may be changed wholesale before any code is written.

- **Language/UI:** Kotlin + Jetpack Compose (Compose-first, Material 3). Proposed minimum SDK
  **API 26 (Android 8.0)**; target/compile SDK **36**.
- **Architecture:** Not yet fixed. If the repo already contains boilerplate, read it and follow the
  established pattern. Otherwise the proposed default is **MVVM + unidirectional data flow with Hilt and
  Coroutines/Flow** — but confirm with the developer per §5.1 before scaffolding, and record the agreed
  choice as an ADR.
- **Dependencies:** Proposed **Gradle (Kotlin DSL) with a version catalog** (`gradle/libs.versions.toml`).
- **Concurrency:** Proposed Kotlin Coroutines + Flow with structured concurrency.
- **Offline/persistence:** Proposed **Room + WorkManager + DataStore**.
- **Testing/quality:** Proposed JUnit + MockK + Turbine + Compose UI test + Espresso + Robolectric;
  **Kover** coverage; **ktlint + detekt + Android Lint**.
- **CI/CD:** Proposed GitHub Actions + Fastlane + Google Play tracks — see the
  [Android DevOps agent](.github/agents/android-devops.agent.md).
- **Identity/signing (DevOps):** Proposed `applicationId` `mmo.catchrecording.android` (+ `.dev`/`.test`
  flavors), Play App Signing + upload key, release tag prefix `android-v*`.

### 5.1 Tech-stack confirmation gate (mandatory before any code implementation)

Because §5 is tentative, **no agent may begin code implementation (app code _or_ pipeline/config code)
until the tech stack has been explicitly confirmed by the Android developer.** This gate is separate from,
and comes **before**, the §4 plan-approval gate.

- **Where confirmation status lives:** each implementing agent records it in a **`Tech-stack confirmation
  status`** block at the top of its own agent file —
  [Android Developer](.github/agents/android-developer.agent.md) owns the **app** stack (language, UI,
  architecture, DI, persistence, testing, lint), and [Android DevOps](.github/agents/android-devops.agent.md)
  owns the **pipeline/identity/signing** stack. The status starts as **`❌ Not yet confirmed`**.
- **When still unconfirmed:** before making **any** code/config change (including scaffolding), the agent
  MUST **stop and prompt the developer** to confirm or amend the relevant §5 decisions — even if the user
  did not ask about the tech stack. Present the proposed defaults, capture the developer's confirmations
  and any changes, and record the agreed decisions (as ADRs where architectural).
- **After confirmation:** the agent **updates the `Tech-stack confirmation status` block on its own agent
  file** to `✅ Confirmed` with the date, who confirmed, and a one-line note of any deviations from the
  §5 defaults — and updates §5 here if a default changed. Once marked confirmed, agents **do not re-prompt**
  in later sessions (they read the block first and skip the gate).
- **Trivial fast-path exception:** genuinely trivial, non-code changes (docs/comments) do not require the
  gate. Any change that writes app or pipeline code does.
- **Manual override:** the developer may confirm the whole stack in one go ("the proposed stack is fine,
  mark it confirmed"), or defer specific items — record exactly what was and was not confirmed.

## 6. Build & test commands

Prefer running via Gradle (and Fastlane once configured). Common commands:

- Build: `./gradlew assembleDebug` (or `bundleRelease` for an AAB)
- Unit test: `./gradlew testDebugUnitTest`
- Instrumented/UI test: `./gradlew connectedDebugAndroidTest`
- Lint/format: `./gradlew ktlintCheck detekt lint`
- Fastlane (once set up): `bundle exec fastlane test`, `bundle exec fastlane beta`, `bundle exec fastlane release`

See [ci-cd instructions](.github/instructions/ci-cd.instructions.md) and the
[release pipeline skill](.github/skills/android-release-pipeline/SKILL.md).

## 7. Conventions

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html) and the
  [Android Kotlin style guide](https://developer.android.com/kotlin/style-guide): clarity at the point of
  use, `UpperCamelCase` types, `lowerCamelCase` members, no needless words.
- Keep composables small and stateless where possible; hoist state and push logic into view
  models/use-cases.
- Use ktlint/detekt if configured; do not fight the formatter.
- Conventional, descriptive commits; small PRs; follow DEFRA
  [pull request](https://defra.github.io/software-development-standards/processes/pull_requests/) and
  [version control](https://defra.github.io/software-development-standards/standards/version_control_standards/) standards.
