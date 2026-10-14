# shellcheck shell=bash
# The one shell reader of the app version, shared by the release scripts (ADR 0018).
# shellcheck disable=SC2034 # consumed by the scripts that source this file
VERSION_FILE="version.properties"

# Prints "<VERSION_NAME> <VERSION_CODE>" from the given version.properties.
read_project_version() {
  awk '
    /^[[:space:]]*(#|$)/ { next }
    {
      eq = index($0, "=")
      if (eq == 0) next
      key = substr($0, 1, eq - 1); value = substr($0, eq + 1)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", key); gsub(/^[[:space:]]+|[[:space:]]+$/, "", value)
      if (key == "VERSION_NAME") name = value
      if (key == "VERSION_CODE") code = value
    }
    END { print name, code }
  ' "$1"
}
