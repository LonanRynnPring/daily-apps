#!/usr/bin/env bash
# Waits for the GitHub Actions build of a pushed commit and reports the result.
# Usage: bash scripts/wait-for-build.sh <full-commit-sha> [max-seconds]
# Exit codes: 0 = build succeeded, 1 = build failed (error log printed), 2 = timed out.
# Run it with a generous tool timeout (about 10 minutes).

SHA="$1"
MAX="${2:-540}"
REPO="LonanRynnPring/daily-apps"
SHORT="${SHA:0:7}"

if [ -z "$SHA" ]; then
  echo "usage: wait-for-build.sh <full-commit-sha> [max-seconds]"
  exit 2
fi

START=$(date +%s)
while true; do
  RESP=$(curl -sS -m 20 "https://api.github.com/repos/$REPO/actions/runs?head_sha=$SHA&per_page=5" 2>/dev/null)
  STATE=$(printf '%s' "$RESP" | python3 -c '
import sys, json
try:
    runs = json.load(sys.stdin).get("workflow_runs", [])
    if not runs:
        print("none")
    else:
        r = runs[0]
        print(r.get("status"), r.get("conclusion") or "-")
except Exception:
    print("error")
')
  echo "$(date +%H:%M:%S) build state: $STATE"

  case "$STATE" in
    "completed success")
      echo "BUILD OK for $SHORT"
      exit 0
      ;;
    completed*)
      echo "BUILD FAILED for $SHORT. Fetching error log..."
      sleep 5
      git fetch --depth=1 origin build-logs >/dev/null 2>&1
      FILE=$(git ls-tree -r --name-only FETCH_HEAD 2>/dev/null | grep -- "-$SHORT.log" | head -n 1)
      if [ -n "$FILE" ]; then
        git show "FETCH_HEAD:$FILE"
      else
        echo "No error log was saved. Check the Actions tab on GitHub for $SHA."
      fi
      exit 1
      ;;
  esac

  if [ $(( $(date +%s) - START )) -ge "$MAX" ]; then
    echo "TIMED OUT waiting for build of $SHORT"
    exit 2
  fi
  sleep 30
done
