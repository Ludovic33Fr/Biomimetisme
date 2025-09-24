#!/usr/bin/env sh
read EVENT || exit 0
PID=$(echo "$EVENT" | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2 || true)
if [ -n "$PID" ]; then
  kill -TERM "$PID" 2>/dev/null || true
  sleep 0.2
  kill -KILL "$PID" 2>/dev/null || true
fi
exit 0
