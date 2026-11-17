#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=lib/project-version.sh
source "$(dirname "$0")/lib/project-version.sh"

read -r NAME CODE <<< "$(read_project_version "$VERSION_FILE")"

if [[ -z "${NAME}" || -z "${CODE}" ]]; then
  echo "::error::Could not read VERSION_NAME or VERSION_CODE from ${VERSION_FILE}" >&2
  exit 1
fi

TAG_NAME="v${NAME}-BUILD_${CODE}"

REMOTE_REFS="$(git ls-remote --tags origin "refs/tags/${TAG_NAME}" "refs/tags/${TAG_NAME}^{}")"
if [[ -n "${REMOTE_REFS}" ]]; then
  # The peeled ^{} line (annotated tags) sorts last and is the commit; a lightweight tag has only one line.
  TAGGED_COMMIT="$(tail -n1 <<< "${REMOTE_REFS}" | cut -f1)"
  if [[ "${TAGGED_COMMIT}" == "$(git rev-parse HEAD)" ]]; then
    echo "Tag '${TAG_NAME}' already points at this commit; nothing to do."
    exit 0
  fi
  echo "::error::Tag '${TAG_NAME}' already exists on another commit (${TAGGED_COMMIT}). Increment VERSION_CODE in ${VERSION_FILE} before merging to main." >&2
  exit 1
fi

echo "Creating and pushing release tag: ${TAG_NAME}"
git config user.name "github-actions[bot]"
git config user.email "github-actions[bot]@users.noreply.github.com"
git tag "${TAG_NAME}"
if ! git push origin "${TAG_NAME}"; then
  # GITHUB_TOKEN can't push a tag whose .github/workflows differs from every branch tip (no `workflows` permission).
  BRANCH_TIP="$(git ls-remote origin "${GITHUB_REF}" | cut -f1)"
  if [[ -n "${BRANCH_TIP}" && "${BRANCH_TIP}" != "$(git rev-parse HEAD)" ]]; then
    echo "::error::'${GITHUB_REF_NAME}' moved on to ${BRANCH_TIP} during this run and its workflow files now differ from the commit being tagged. Re-run Android CI on the latest commit." >&2
  fi
  exit 1
fi
