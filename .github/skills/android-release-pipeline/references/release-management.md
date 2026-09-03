# Release management: development → production (Android)

This reference describes the end-to-end release flow the pipeline implements for the MMO Catch Recording
Android app. It is **Android-only** and assumes **trunk-based development with tag-driven releases** — there
are **no release branches**. See [ci-cd.instructions.md](../../../instructions/ci-cd.instructions.md) for the
governing standards.

## Roles (small team)
- **Developer** — writes app code on short-lived feature branches, opens PRs into `main`.
- **Internal QA** — smoke/exploratory validation on each app's internal testing track.
- **UAT (business users)** — acceptance testing on the **Test** app's closed testing track.
- **DevOps** — owns the pipeline, cuts release tags, approves the gated Environments, monitors production.

## The flow

```
1. Develop        feature/* branch  ──PR──▶  main
                    PR CI: ktlint/detekt/lint · build · unit/UI tests + coverage (Kover) · SonarCloud (PR)
                    Native gates: CodeQL · Dependabot · secret scanning + push protection
                    Branch protection: green CI + review required to merge

2. Integrate      merge to main (trunk, always releasable)
                    main CI: full tests + SonarCloud (main) + quality gate

3. Cut a release  push tag  android-vX.Y.Z  on main
                    versionName  = X.Y.Z (from tag)
                    versionCode   = release GITHUB_RUN_NUMBER (no Play API query)
                    GIT_COMMIT_SHA = read-only BuildConfig metadata (traceability only)

4. Dev            [env: dev — no gate]
                    build the Dev app AAB (mmo.catchrecording.android.dev) from the tagged commit
                    upload_to_play_store(track: internal) → Dev internal track

5. Test           [env: test — APPROVAL A] build the Test app AAB (mmo.catchrecording.android.test) ──▶
                    upload_to_play_store(track: internal) → Test internal track  (smoke / exploratory sign-off)
                  [env: test-closed — APPROVAL B] promote the SAME Test build → closed UAT track
                    (track_promote_to: closed; no rebuild) → business UAT sign-off

6. Prod           [env: prod — APPROVAL C] build the Prod app AAB (mmo.catchrecording.android) ──▶
                    upload_to_play_store(track: internal) → Prod internal track  (production-identity smoke)
                  [env: prod-closed — APPROVAL D] promote the SAME Prod build → closed track (no rebuild)

7. Production     [env: prod-production — APPROVAL E]
                    promote the SAME Prod build → production track → STAGED (percentage) ROLLOUT

8. Monitor        Play Console vitals (ANRs, crashes) + crash reporting during the staged roll-out
                    halt the staged rollout if regressions appear

9. Hotfix         fix on main  →  new higher patch tag  android-vX.Y.(Z+1)
                    (no hotfix/release branch — the trunk is always releasable)
```

> **Build-per-environment from one tested commit.** The three apps have distinct `applicationId`s, so a
> single AAB cannot move between environments; one tag builds all three from the **same commit**.
> Equivalence is evidenced by the same commit SHA, pinned toolchain and locked dependencies, plus the Prod
> app's own internal (and, where required, closed) testing pass before production. Track promotion within a
> Play app is a **no-rebuild** Google Play metadata action (`track_promote_to`).

## Why no release branches
For a single team shipping a single live version, a release branch adds merge/maintenance overhead without
benefit. A Git tag on `main` is an immutable, auditable release point; the gated Environments provide the
control that a release branch would otherwise gate. Release branches would only be justified to stabilise a
release while `main` moves on, or to support multiple live versions in parallel — neither applies here. Any
future need is an ADR + governance discussion, not an ad hoc branch.

## Versioning rules
- **`versionName`**: SemVer from the tag (`android-v1.4.0` → `1.4.0`). The single human-facing version.
- **`versionCode`**: from the **release** `GITHUB_RUN_NUMBER`; **not** queried from the Play API. Must be
  **unique and higher** than the previous upload (Google Play rejects a reused or lower `versionCode`);
  never reused or hand-edited.
- **Commit SHA**: embedded as a read-only `BuildConfig` field (e.g. `GIT_COMMIT_SHA`) for traceability only
  — it is never the version code.

## Approval & environments
**Six** Environments — **`dev`** (ungated), **`test`** (A), **`test-closed`** (B), **`prod`** (C),
**`prod-closed`** (D) and **`prod-production`** (E) — each gated (except `dev`) by a **manual reviewer
approval** before its job runs (prevent self-approval where supported), and each scopes its release secrets
to the Environment (exposed only after that stage's approval). A stage is only reachable once the preceding
gate is approved. Restrict deployments to `main` and `android-v*` tags.

## Traceability
Every production build is traceable end to end: **tag → commit SHA → workflow run → versionName →
versionCode → AAB checksums → Play Console build → tracks & sign-off → production version & rollout
status**. Keep release notes tester-friendly for the testing tracks ("what to test", "known limitations").
