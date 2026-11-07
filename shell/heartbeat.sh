#!/usr/bin/env bash
# Nibras liveness: 🟢 when BOTH the backend (heartbeat API) and the frontend (SPA index) are up,
# 🔴 otherwise. Exit 0 = up, 1 = down.
#   ./heartbeat.sh
#   NIBRAS_URL=http://host:8081 ./heartbeat.sh
URL="${NIBRAS_URL:-http://127.0.0.1:8081}"

back=0; front=0
curl -sf --max-time 3 "$URL/page/heartbeatJson" 2>/dev/null | grep -q '"result":"ok"' && back=1
curl -sf --max-time 3 "$URL/" 2>/dev/null | grep -qi 'id="root"' && front=1

if [ "$back" = 1 ] && [ "$front" = 1 ]; then
    echo "🟢 up   ($URL)"
    exit 0
else
    echo "🔴 down  backend=$([ $back = 1 ] && echo ok || echo x)  frontend=$([ $front = 1 ] && echo ok || echo x)  ($URL)"
    exit 1
fi
