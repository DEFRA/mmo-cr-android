# 0022 - Android release topology: three Play apps, six environments

## Status

Accepted (2026-11-04). Supersedes [0025](0025-android-release-poc.md) (release POC); works with
[0023](0023-android-compile-once-package-per-environment.md) (how the artifacts are built).

## Context

The Android app must reach six delivery stages — Dev, Test, Perf-Test, UAT (Ext-Test), Prep-Prod and
Production — each against its own CDP backend, like the iOS app. The POC (ADR 0025) used one Play app with
closed tracks per backend. A spike on Google Play showed that this couples the stages:

- Closed and production tracks of one app share a single *Publish changes* queue. A change in review blocks
  or restarts another stage's review, and Managed Publishing holds every stage's release together.
- Internal testing uploads never enter review and never affect the closed-track or production reviews.
- The Play API cannot click *Publish changes*, and any non-internal commit made while another change is in
  review restarts that review (`changesInReviewBehavior` defaults to restarting it).

## Decision

### Three Play apps, six environments

| Env key | App package | Managed Publishing | Play track | GitHub Environment (gate) | Backend |
|---|---|---|---|---|---|
| dev | `mmo.catchrecordingdev.android` | OFF | internal | `dev` (none) | Dev |
| test | `mmo.catchrecordingtest.android` | OFF | internal | `test` | Test |
| perf-test | `mmo.catchrecordingtest.android` | OFF | closed `perf-test` (publishes automatically after Google review) | `perf-test` | Perf-Test |
| uat | `mmo.catchrecording.android` | ON | internal (never reviewed or held) | `uat` | Ext-Test |
| prep-prod | `mmo.catchrecording.android` | ON | closed `prep-prod` | `prep-prod` | Prod |
| production | `mmo.catchrecording.android` | ON | `production` | `production` | Prod |

- **Managed Publishing is ON only in the Prod app.** Dev and Test releases go live as soon as Google has
  reviewed them, so a stage never waits for a manual click; Prod changes wait for a person.
- Track names `perf-test` and `prep-prod` must match exactly; they are created by hand in Play Console.
- All gated environments require reviewers with *prevent self-review*, and deploy only from `v*` tags.
  `dev` has no reviewers.

### Prep-prod, sanity, promote, timed go-live

1. `prep-prod` uploads to the closed `prep-prod` track; the release manager clicks *Publish changes* once
   Google's review reaches *Ready*, and the sanity testers install it.
2. After sanity sign-off, `production` **promotes the same versionCode** already on `prep-prod`. It never
   uploads, so production is byte-for-byte what was sanity-tested. It refuses to run unless `prep-prod`
   holds the code.
3. After review the release manager clicks *Publish changes* at the agreed go-live time. There is no
   GitHub job for go-live.

### Versioning

`VERSION_CODE` in `version.properties` is the build number **N** (raised by every merged PR, never reset,
`1..9999`). The tag stays `v<VERSION_NAME>-BUILD_<N>`. The uploaded versionCode is
`channel × 10 000 + N`, with channel 2 for internal tracks and 1 for closed tracks (the Dev app has one
channel and uses 1). For N = 15: dev 10015, test 20015, perf-test 10015, uat 20015, prep-prod 10015,
production 10015 (prep-prod's code). Within an app the internal channel is higher than the closed one, so
a code is never ambiguous between tracks. The Play API is never queried to choose a version.

### Review-safe commits

Closed and production commits use `changesInReviewBehavior: ERROR_IF_IN_REVIEW`; the lane fails with
"Another change in `<package>` is in review; re-run after it completes. No review was restarted." Internal
commits use the default, as internal uploads never enter review. A re-run is idempotent: a versionCode
already on the target track is a success (the stage is skipped), one on a different track of the same app
is an error, and a bundle an earlier attempt uploaded but never released is released without a new upload.

### Resuming a run

A failed or rejected stage is resumed with *Re-run failed jobs*, which repeats only that stage and the ones
after it. *Re-run all jobs* is also safe: `compile` reuses the run's `release-base` artifact instead of
rebuilding (compile once holds across attempts), and stages already on Play are skipped. A new run on the
same tag recompiles, so a re-run is preferred; re-runs are possible for 30 days.

### Distribution modes

The release workflow takes `distribution`:

- **`manual`** (default) — the job builds, signs and verifies the AAB and publishes it as a 7-day workflow
  artifact, then prints the Play Console steps. Used for the first upload of each new app, and whenever
  Play API access is unavailable.
- **`play`** — the job also uploads (or, for `production`, promotes) through the Play Developer API.

### Switching tracks on one device (prerequisite)

This decides [ADR 0021](0021-local-data-separation-per-backend-environment.md) (option 2, process control).

The Prod app serves two backends under one package (`uat` = Ext-Test; `prep-prod`/`production` = Prod) and
the Test app serves two (`test`, `perf-test`). The backend is fixed by the packaged config of the installed
build, and the app keeps one local store (drafts, reference-data cache, queued submissions) with no
per-backend separation.

**Prerequisite: switching a device between tracks that use different backends must always uninstall the
existing app first, and so remove its data, before installing from the other track.** Unsynced drafts are
lost, so submit or discard them beforehand. Uninstalling deletes the app's private storage (database,
wrapped passphrase, DataStore, queued work) and its Keystore key, so the next install starts empty against
its own backend. *Archiving* an app (Android 15+) keeps its data and does **not** count as uninstalling.

Two rules keep the prerequisite reliable:

- **One track per tester per app.** Internal tracks have the higher versionCode (2 against 1), so a Google
  account eligible for both `uat` and `prep-prod`/`production` is offered the `uat` build as an ordinary
  in-place update, which keeps the Prod-backend data. The reverse is a downgrade, which Android refuses, so
  it already forces an uninstall. Keep UAT testers off the `prep-prod` list and use devices that never held
  the production build.
- **The release manager checks the tester lists** when a track is created or changed (README one-off setup).

A technical guard (remembering the backend id and refusing to show or submit data made for another
backend) was considered and is not adopted; the app therefore relies on this procedure. Residual risk if
it is broken: Prod-backend drafts reaching the Ext-Test backend or the reverse. Revisit if that happens or
if an audit requires a technical control.

### Accepted deviation DA-1: one Play service account

A **single** Play service account has release permissions on all three apps and is stored as
`PLAY_SERVICE_ACCOUNT_JSON` only in GitHub Environments (including `production`). Per-app accounts were
rejected as disproportionate for a small team. Mitigations: release tags are created only from protected
`main`; every Environment deploys only from `v*` tags and (except `dev`) needs a reviewer; CODEOWNERS on
`.github/workflows/` and `fastlane/` is recommended. To be logged with Delivery Architecture.

## Alternatives rejected

- **One app, many closed tracks (ADR 0025).** The shared review and *Publish changes* queue proved in the
  spike to couple stages (see Context).
- **Internal App Sharing.** Links expire and are not tracked as releases, so there is no audit trail or
  tester management.
- **Firebase App Distribution.** Adds a second distribution platform and credentials, and its builds are not
  the Play-installed package that the Prod stages must prove.

## Consequences

- Three Play apps to maintain: store listing, testers and a first manual upload each. The runbook is in the
  README *Release* section.
- Dev and Test builds are separate packages and install beside the Prod app; the Prod app serves the
  Ext-Test and Prod backends, so a device must be uninstalled before it switches track (see *Switching
  tracks on one device*); the app does not keep per-backend local data.
- Packages are repackaged from one compiled bundle rather than compiled per app (ADR 0023).
- Perf-test, prep-prod and production each wait for Google review; a second change to the same app is
  blocked until the first is published.
