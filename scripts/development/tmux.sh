#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../.."
command -v tmux >/dev/null || { echo 'Install tmux, or use make dev instead.' >&2; exit 1; }
# A named session is reused, never duplicated. Infrastructure is prepared only for a new session.
if ! tmux has-session -t '=rent-cars' 2>/dev/null; then
  make dev-prepare
  printf -v root '%q' "$PWD"
  command="env"
  for variable in HDFS_ENABLED SERVER_PORT API_TARGET AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY; do
    if [[ -n "${!variable:-}" ]]; then
      printf -v value '%q' "${!variable}"
      command+=" $variable=$value"
    fi
  done
  command+=" bash scripts/development/app.sh"
  tmux new-session -d -s rent-cars -n tools -c "$PWD"
  tmux set-option -t rent-cars remain-on-exit on
  for app in api web recommendations; do
    tmux new-window -t rent-cars -n "$app" -c "$PWD" "cd $root && $command $app"
  done
  echo 'Session started. Ctrl+b d detaches without stopping applications.'
fi
if [[ -n "${TMUX:-}" ]]; then
  exec tmux switch-client -t rent-cars
fi
exec tmux attach-session -t rent-cars
