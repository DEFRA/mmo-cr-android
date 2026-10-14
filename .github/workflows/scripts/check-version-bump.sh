#!/usr/bin/env bash
# PR-time guard: a merged PR must produce a new, higher release tag. Dependabot PRs are skipped by the workflow.
set -euo pipefail
# shellcheck source=lib/project-version.sh
source "$(dirname "$0")/lib/project-version.sh"

fail() {
  echo "::error file=${VERSION_FILE}::$1" >&2
  exit 1
}

read -r NAME CODE <<< "$(read_project_version "$VERSION_FILE")"
[[ "${NAME:-}" =~ ^[0-9]+(\.[0-9]+)*$ ]] || fail "VERSION_NAME '${NAME:-}' must look like 1.2.3"
[[ "${CODE:-}" =~ ^[1-9][0-9]*$ ]] || fail "VERSION_CODE '${CODE:-}' must be a whole number"
TAG="v${NAME}-BUILD_${CODE}"

set +e
git ls-remote --exit-code --tags origin "refs/tags/${TAG}" >/dev/null
TAG_LOOKUP=$?
set -e
case "$TAG_LOOKUP" in
  0) fail "Release tag ${TAG} already exists. Bump VERSION_CODE (and VERSION_NAME if needed)." ;;
  2) ;;
  *) fail "Could not query remote tags (git exit ${TAG_LOOKUP}); refusing to assume ${TAG} is new." ;;
esac

# Compare with the latest target branch so a PR that is behind can't reuse a code already merged.
BASE_FILE="$(mktemp)"
if ! git show "origin/${GITHUB_BASE_REF:?}:${VERSION_FILE}" > "$BASE_FILE" 2>/dev/null; then
  rm -f "$BASE_FILE"
  echo "${GITHUB_BASE_REF} has no ${VERSION_FILE} yet; merging this PR will publish ${TAG}."
  exit 0
fi
read -r BASE_NAME BASE_CODE <<< "$(read_project_version "$BASE_FILE")"
rm -f "$BASE_FILE"

LOWEST="$(printf '%s\n%s\n' "$NAME" "$BASE_NAME" | sort -V | head -n1)"
if [[ "$NAME" != "$BASE_NAME" && "$LOWEST" == "$NAME" ]]; then
  fail "VERSION_NAME ${NAME} is lower than ${BASE_NAME} on ${GITHUB_BASE_REF}."
fi
# Unlike iOS build numbers, Android versionCode never resets: it must rise on every release.
if [[ "$BASE_CODE" =~ ^[0-9]+$ ]] && (( CODE <= BASE_CODE )); then
  fail "VERSION_CODE ${CODE} must be higher than ${BASE_CODE} on ${GITHUB_BASE_REF}."
fi

echo "Version bump OK: ${GITHUB_BASE_REF} has ${BASE_NAME} (${BASE_CODE}); merging this PR will publish ${TAG}."
