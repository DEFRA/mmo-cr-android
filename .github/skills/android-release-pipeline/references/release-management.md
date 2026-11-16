# Release management: development → production (Android)

This reference describes the end-to-end release flow the pipeline implements for the MMO Catch Recording
Android app. It is **Android-only** and assumes **trunk-based development with tag-driven releases** — there
are **no release branches**. See [ci-cd.instructions.md](../../../instructions/ci-cd.instructions.md) for the
governing standards and ADRs 0022/0023 for the design.

## Roles (small team)
- **Developer** — writes app code on short-lived feature branches, opens PRs into `main`.
- **Internal QA** — smoke/exploratory validation on the internal tracks.
- **UAT / sanity testers** — acceptance on the Perf-Test and Prep-Prod closed tracks.
- **Release manager** — starts releases, approves the gated Environments and clicks **Publish changes** in
  Play Console for the Prod app.
- **DevOps** — owns the pipeline and monitors production.

## The flow

```
1. Develop        feature/* branch  ──PR──▶  main
                    PR CI: ktlint/detekt/lint · build · unit/UI tests + coverage (Kover) · SonarCloud (PR)
                    Native gates: CodeQL · Dependabot · secret scanning + push protection
                    Branch protection: green CI + review required to merge

2. Integrate      merge to main (trunk, always releasable)
                    main CI: full tests + SonarCloud (main) + quality gate

3. Tag           release-tag job (android-ci.yml, main only) creates  v<VERSION_NAME>-BUILD_<N>
                    N = VERSION_CODE in version.properties (1..9999); no Play API query
                    GIT_COMMIT_SHA = read-only BuildConfig metadata (traceability only)

4. Release        Android Release (workflow_dispatch on the tag; inputs dev / test / prod, distribution manual|play)
                  [compile — no gate] one unsigned, config-free base bundle, repackaged per environment

5. Dev / Test     [env: dev — no gate]   Dev app  · internal    (code 1·10000+N)
                  [env: test — gate]     Test app · internal    (code 2·10000+N)
                  [env: perf-test — gate] Test app · closed track perf-test (code 1·10000+N); publishes
                    automatically after Google review (Managed Publishing off)

6. Prod           [env: uat — gate]      Prod app · internal    (code 2·10000+N; never reviewed or held)
                  [env: prep-prod — gate] Prod app · closed track prep-prod (code 1·10000+N)
                    → Google review → release manager clicks Publish changes → sanity testers

7. Production     [env: production — gate] after sanity sign-off: PROMOTE the prep-prod code to production
                    (no upload) → Google review → release manager clicks Publish changes at the go-live time

8. Monitor        Play Console vitals (ANRs, crashes) + crash reporting after go-live

9. Hotfix         fix on main  →  next build number N  →  new tag  (no hotfix/release branch)
```

> **Compile once, package per environment.** One tag yields one compiled base; each environment job
> repackages it (package name, versionCode, config asset), signs it and runs `release-tools verify`, so the
> binary differs only in those entries. Non-internal Play commits use `ERROR_IF_IN_REVIEW` so a commit never
> restarts an in-flight review. `manual` distribution leaves the signed AAB as a 7-day artifact for upload in
> Play Console (always the case for each app's first upload).

## Why no release branches
For a single team shipping a single live version, a release branch adds merge/maintenance overhead without
benefit. A Git tag on `main` is an immutable, auditable release point; the gated Environments provide the
control that a release branch would otherwise gate. Release branches would only be justified to stabilise a
release while `main` moves on, or to support multiple live versions in parallel — neither applies here. Any
future need is an ADR + governance discussion, not an ad hoc branch.

## Versioning rules
- **`versionName`**: SemVer from `version.properties` (`VERSION_NAME`), also in the tag
  `v<VERSION_NAME>-BUILD_<N>`. The single human-facing version.
- **`N`** (`VERSION_CODE`): build number, raised by every merged PR, never reset, 1..9999. The uploaded
  versionCode is `channel × 10 000 + N` (internal = 2, closed = 1). Never queried from the Play API, never
  reused or hand-edited.
- **Commit SHA**: embedded as a read-only `BuildConfig` field (e.g. `GIT_COMMIT_SHA`) for traceability only
  — it is never the version code.

## Approval & environments
**Six** Environments — `dev` (ungated), `test`, `perf-test`, `uat`, `prep-prod` and `production` — each gated
(except `dev`) by **required reviewers** with self-review prevented, deploying only from `v*` tags. Each
scopes its secrets to the Environment (exposed only after approval). The chain runs in order; `perf-test`,
`prep-prod` and `production` also need `test`, `uat` and `prep-prod` to have succeeded.

## Traceability
Every production build is traceable end to end: **tag → commit SHA → workflow run (step summary: package,
versionCode, base and package SHA-256) → Play Console build → track & sign-off → production release**. Keep
release notes tester-friendly for the testing tracks ("what to test", "known limitations").
