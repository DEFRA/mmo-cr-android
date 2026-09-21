#!/usr/bin/env bash
set -euo pipefail

# Converts Gradle JUnit XML to SonarQube's generic testExecutions format.
# Kotlin has no native JUnit importer, so sonar.testExecutionReportPaths is the only route.

RESULTS_DIR="${1:-app/build/test-results/testDebugUnitTest}"
OUTPUT_FILE="${2:-build/sonar/test-execution.xml}"
SOURCE_ROOTS=("app/src/test/java" "app/src/androidTest/java")

if [ ! -d "$RESULTS_DIR" ]; then
  echo "No test results at ${RESULTS_DIR} — skipping conversion." >&2
  exit 0
fi

REPORTS=()
while IFS= read -r report; do
  REPORTS+=("$report")
done < <(find "$RESULTS_DIR" -maxdepth 1 -name '*.xml' | sort)

if [ "${#REPORTS[@]}" -eq 0 ]; then
  echo "No JUnit XML found in ${RESULTS_DIR} — skipping conversion." >&2
  exit 0
fi

# Values stay XML-escaped end to end: an attribute can never hold a raw quote and
# element text can never hold a raw '<', so the input escaping is passed straight through.
extract_cases() {
  awk '
    function attr(text, name,   needle, rest) {
      needle = name "=\""
      if (match(text, needle) == 0) return ""
      rest = substr(text, RSTART + length(needle))
      return substr(rest, 1, index(rest, "\"") - 1)
    }
    { doc = doc $0 "\n" }
    END {
      pos = 1
      while (1) {
        rel = index(substr(doc, pos), "<testcase")
        if (rel == 0) break
        start = pos + rel - 1
        tag_end = start + index(substr(doc, start), ">") - 1
        open_tag = substr(doc, start, tag_end - start + 1)

        if (substr(doc, tag_end - 1, 1) == "/") {
          body = ""
          pos = tag_end + 1
        } else {
          close_rel = index(substr(doc, tag_end), "</testcase>")
          body = substr(doc, tag_end + 1, close_rel - 2)
          pos = tag_end + close_rel + 10
        }

        kind = ""
        if (index(body, "<failure") > 0) kind = "failure"
        else if (index(body, "<error") > 0) kind = "error"
        else if (index(body, "<skipped") > 0) kind = "skipped"

        message = ""
        detail = ""
        if (kind != "") {
          det_start = index(body, "<" kind)
          det = substr(body, det_start)
          message = attr(det, "message")
          det_tag_end = index(det, ">")
          if (substr(det, det_tag_end - 1, 1) != "/") {
            det_close = index(det, "</" kind ">")
            detail = substr(det, det_tag_end + 1, det_close - det_tag_end - 1)
          }
          gsub(/^[ \t\n]+|[ \t\n]+$/, "", detail)
          gsub(/\n/, "\\&#10;", detail)
        }

        printf "%s\037%s\037%d\037%s\037%s\037%s\n", \
          attr(open_tag, "classname"), attr(open_tag, "name"), \
          (attr(open_tag, "time") * 1000) + 0.5, kind, message, detail
      }
    }
  ' "$1"
}

resolve_source_file() {
  local fqn="${1%%\$*}"
  local relative="${fqn//.//}.kt"
  local root
  for root in "${SOURCE_ROOTS[@]}"; do
    if [ -f "${root}/${relative}" ]; then
      printf '%s' "${root}/${relative}"
      return 0
    fi
  done

  # Kotlin permits a file name that differs from the class it declares.
  local match
  match=$(find "${SOURCE_ROOTS[@]}" -name "${fqn##*.}.kt" 2>/dev/null | head -n 1)
  [ -n "$match" ] || return 1
  printf '%s' "$match"
}

ROWS=$(mktemp)
UNRESOLVED=$(mktemp)
trap 'rm -f "$ROWS" "$UNRESOLVED"' EXIT

total=0
for report in "${REPORTS[@]}"; do
  while IFS=$'\037' read -r classname name duration kind message detail; do
    [ -n "$classname" ] || continue
    total=$((total + 1))
    if source_file=$(resolve_source_file "$classname"); then
      printf '%s\037%s\037%s\037%s\037%s\037%s\n' \
        "$source_file" "$name" "$duration" "$kind" "$message" "$detail" >>"$ROWS"
    else
      echo "$classname" >>"$UNRESOLVED"
    fi
  done < <(extract_cases "$report")
done

resolved=$(wc -l <"$ROWS" | tr -d ' ')
if [ "$resolved" -eq 0 ]; then
  echo "::error::Parsed ${total} test case(s) but resolved no source files — check SOURCE_ROOTS." >&2
  exit 1
fi

mkdir -p "$(dirname "$OUTPUT_FILE")"
{
  echo '<?xml version="1.0" encoding="UTF-8"?>'
  echo '<testExecutions version="1">'
  sort "$ROWS" | awk -F'\037' '
    {
      if ($1 != current) {
        if (current != "") print "  </file>"
        printf "  <file path=\"%s\">\n", $1
        current = $1
      }
      if ($4 == "") {
        printf "    <testCase name=\"%s\" duration=\"%s\"/>\n", $2, $3
      } else {
        printf "    <testCase name=\"%s\" duration=\"%s\">\n", $2, $3
        printf "      <%s message=\"%s\">%s</%s>\n", $4, ($5 == "" ? $4 : $5), $6, $4
        print  "    </testCase>"
      }
    }
    END { if (current != "") print "  </file>" }
  '
  echo '</testExecutions>'
} >"$OUTPUT_FILE"

files=$(cut -d$'\037' -f1 "$ROWS" | sort -u | wc -l | tr -d ' ')
if [ -s "$UNRESOLVED" ]; then
  echo "Unresolved test classes (skipped): $(sort -u "$UNRESOLVED" | paste -sd, -)" >&2
fi
echo "Wrote ${OUTPUT_FILE}: ${files} file(s), ${resolved} test case(s)."
