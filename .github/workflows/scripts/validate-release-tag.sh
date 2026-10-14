#!/usr/bin/env bash
set -euo pipefail
# shellcheck source=lib/project-version.sh
source "$(dirname "$0")/lib/project-version.sh"

read -r NAME CODE <<< "$(read_project_version "$VERSION_FILE")"

if [[ -z "${NAME}" || -z "${CODE}" ]]; then
  echo "::error::Could not read VERSION_NAME or VERSION_CODE from ${VERSION_FILE}" >&2
  exit 1
fi

# The version comes only from the code: the run must be on the tag that code produces, never a branch or override.
EXPECTED_TAG="v${NAME}-BUILD_${CODE}"

if [[ "${GITHUB_REF_TYPE:-}" != "tag" ]]; then
  echo "::error::Run Android Release on a release tag (Use workflow from → Tags → ${EXPECTED_TAG}), not the branch '${GITHUB_REF_NAME:-}'." >&2
  exit 1
fi

if [[ "${GITHUB_REF_NAME:-}" != "${EXPECTED_TAG}" ]]; then
  echo "::error::Tag ${GITHUB_REF_NAME:-} does not match the version in the code at that tag (${EXPECTED_TAG})." >&2
  exit 1
fi

echo "Releasing ${EXPECTED_TAG}: versionName ${NAME}, versionCode ${CODE}, taken from the code at the tag."
