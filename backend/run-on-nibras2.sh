#!/usr/bin/env bash
# Launch the Nibras backend against db_nibras2 (the migrated production copy). Once it's up, this
# also builds the full-text index and fills the parameter chip colours (both idempotent).
# Ctrl-C stops the app. Run db/migrate-to-nibras2.sh first to create db_nibras2.
#
#   ./run-on-nibras2.sh              # launch on db_nibras2 (+ reindex + fill-colors)
#   SKIP_INIT=1 ./run-on-nibras2.sh  # launch only (no reindex/fill-colors)
#   DST=db_nibras  ./run-on-nibras2.sh   # or point at any database
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
DST="${DST:-db_nibras2_s}"
PORT="${PORT:-8081}"
export SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/${DST}?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Africa/Cairo"

# Free the port if another backend is already running on it.
EXIST="$(lsof -ti tcp:"${PORT}" 2>/dev/null || true)"
if [ -n "$EXIST" ]; then
  echo "Stopping existing backend on :${PORT} (pid ${EXIST})…"
  kill $EXIST 2>/dev/null || true
  sleep 3
fi

echo "Launching backend on ${DST} (port ${PORT})…"

if [ "${SKIP_INIT:-0}" != "1" ]; then
  ( # background: wait for readiness, then a one-time init
    for _ in $(seq 1 90); do
      if curl -sf "http://localhost:${PORT}/api/info" -o /dev/null 2>/dev/null; then
        db="$(curl -s "http://localhost:${PORT}/api/info" | sed -n 's/.*"dbName":"\([^"]*\)".*/\1/p')"
        echo "  [init] backend up on db=${db}; reindexing + filling colours…"
        curl -sf -X POST "http://localhost:${PORT}/api/search/reindex" -o /dev/null \
          && echo "  [init] search reindexed" || echo "  [init] reindex failed"
        curl -sf -X POST "http://localhost:${PORT}/api/lookups/fill-colors" -o /dev/null \
          && echo "  [init] colours filled" || echo "  [init] fill-colors failed"
        exit 0
      fi
      sleep 2
    done
    echo "  [init] backend didn't come up in time; skipping init"
  ) &
fi

cd "$HERE"
exec ./gradlew bootRun
