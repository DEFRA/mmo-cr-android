# 0018 - Android release pipeline (POC)

## Status

Accepted for the release POC (2026-10-14). The end-to-end design (all tracks, approval gates, production,
compile-once repackaging) will supersede or extend this ADR.

## Context

The POC proves how Google Play tracks behave before the full release workflow is designed. It mirrors the
final iOS release structure (version in code, CI-published tags, a manually started release on the tag,
Fastlane lanes with logic in `fastlane/lib/cr_release`), adapted to Android.

## Decision

1. **Version in code.** [`version.properties`](../../version.properties) holds `VERSION_NAME` and
   `VERSION_CODE`; Gradle, the shell scripts (`.github/workflows/scripts/lib/project-version.sh`) and
   Fastlane (`CrRelease::Version`) each read it in one place, so an automated bump only rewrites this file.
   PRs must raise `VERSION_CODE` (Android codes never reset, unlike iOS build numbers) and not lower
   `VERSION_NAME`. The Play API is never queried to choose a version.
2. **Tags.** After CI passes on `main` (not a Dependabot merge), the `release-tag` job publishes
   `v<VERSION_NAME>-BUILD_<VERSION_CODE>`. CI never starts a release.
   **POC-only deviation (revert after the POC):** a manual *Android CI* run with `publish_release_tag`
   may tag **any branch**, so the POC can release from a feature branch. To revert, restore
   `github.ref == 'refs/heads/main'` in the `workflow_dispatch` condition of the `release-tag` job in
   `.github/workflows/android-ci.yml` and remove this note.
3. **Release workflow.** `android-release.yml` is started manually on a tag with `target`:
   `build-only` (signed AAB artifact, 1 day), `internal` (internal testing track) or `dev` (closed testing
   track `dev`). It runs in the ungated `dev` GitHub Environment, which allows deployments from `v*` tags
   only. The run refuses a branch or a tag that does not match `version.properties`.
4. **One target per tag.** Each upload needs an unused versionCode; the lane stops before compiling if the
   code is already on the internal or dev track. Sending a build to another track means a version bump.
5. **Signing.** Play App Signing (Google-generated app signing key, chosen at the manual first upload).
   CI holds only the **upload key**, as Environment secrets (base64 PKCS12 keystore, alias, passwords). The
   lane decodes it into a 0600 temp file that is always deleted, signs the unsigned release AAB with
   `jarsigner` (passwords via `-storepass:env`/`-keypass:env`, never on a command line) and verifies it.
   **fastlane match is not used:** it only manages Apple certificates and profiles.
6. **Play API (parked).** Automated uploads (`internal` / `dev` targets) need a service-account JSON key in
   the Environment secret `PLAY_SERVICE_ACCOUNT_JSON`, with app permissions limited to viewing app
   information and releasing to testing tracks. This is **parked** — see *Play API access* below — and the
   POC uploads by hand.
7. **First upload.** Play requires the very first AAB to be uploaded in Play Console; the `build-only`
   target produces it.

## Play API access (prerequisite, parked)

The Google Play Developer API accepts only Google OAuth credentials, so **every automated upload needs a
Google Cloud project**. Play Console no longer needs a project linked to it; a service account from any
project is invited under *Users and permissions*. No project is associated with the app yet.

| Option | Cloud project | Outcome |
|---|---|---|
| Service account + JSON key | Yes (no billing, no resources) | **Chosen target** — simplest automated route |
| Service account + Workload Identity Federation (GitHub OIDC) | Yes | Keyless; preferred if DEFRA policy blocks service-account keys |
| Personal OAuth credentials (refresh token) | Yes (OAuth client) | Rejected: ties releases to one person, broad permissions, long-lived personal token in CI |
| Manual upload in Play Console | No | **POC interim** — `build-only` artifact uploaded by hand |

To unpark: request a **DEFRA-owned** Google Cloud project (never a personal one); enable the Google Play
Android Developer API; create the service account with no Cloud roles; invite it in Play Console with only
*View app information (read-only)* and *Release apps to testing tracks*; store the key as
`PLAY_SERVICE_ACCOUNT_JSON` on the `dev` Environment (or adopt Workload Identity Federation). The `internal`
and `dev` targets then work without code changes.

## Consequences

- Release builds are clean, uncached builds from the tagged sources (`cache-disabled` Gradle setup).
- Until the Play API access above is in place, every POC upload is manual: run `build-only`, download the
  artifact and upload it to internal testing or the `dev` closed track in Play Console, bumping
  `VERSION_CODE` between uploads. The `internal` / `dev` targets fail without `PLAY_SERVICE_ACCOUNT_JSON`.
- The upload key (and, later, the Play key) sit in an ungated Environment for the POC (acceptable: an upload
  key can be reset in Play Console, and the service account cannot release to production). Gating is part
  of the end-to-end design.
- The `build-only` artifact is downloadable by signed-in GitHub users for one day (public repository); it
  contains only the DEV configuration and no secrets.
