#!/usr/bin/env bash
# Merging while a main run is active can reject its tag push or cancel a pending main run, losing its tag.
set -euo pipefail

ACTIVE_RUNS="$(gh run list --repo "${GITHUB_REPOSITORY}" --workflow "${CI_WORKFLOW_FILE}" --branch main --limit 20 \
  --json status,event,url --jq '.[] | select(.status != "completed" and .event != "merge_group") | .url')"

if [[ -n "${ACTIVE_RUNS}" ]]; then
  while read -r RUN_URL; do
    echo "::error::Android CI is still running on main: ${RUN_URL}"
  done <<< "${ACTIVE_RUNS}"
  echo "::error::Re-add this PR to the merge queue once that run finishes, or cancel it if its release tag isn't needed." >&2
  exit 1
fi

echo "No active Android CI runs on main; safe to merge."
