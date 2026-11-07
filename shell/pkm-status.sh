#!/usr/bin/env bash
# Panel-applet status for the Nibras PIM: backend (Spring, :8081) + frontend (Vite, :5173).
# Prints one line: "🟢 API 🟢 UI". Override ports with API_PORT / UI_PORT env vars.
#
# One-PKM variant (green only when both are up):
#   curl -sf http://localhost:8081/page/heartbeatJson -o /dev/null \
#     && curl -sf http://localhost:5173 -o /dev/null && echo "🟢 PKM" || echo "🔴 PKM"

API_PORT="${API_PORT:-1448}"
UI_PORT="${UI_PORT:-5173}"

curl -sf "http://localhost:${API_PORT}/page/heartbeatJson" -o /dev/null && api="🟢 NBR" || api="🔴 NBR"
#curl -sf "http://localhost:${UI_PORT}/" -o /dev/null && ui="🟢 UI" || ui="🔴 UI"
 
echo "${api} ${ui}"
