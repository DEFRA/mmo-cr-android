#!/usr/bin/env bash
# Backstop for the lane's own clean-up: the decoded upload keystore never outlives the job.
set -euo pipefail

rm -f "${RUNNER_TEMP:-/tmp}"/mmo-cr-upload-*.keystore || true
