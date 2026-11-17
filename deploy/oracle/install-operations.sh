#!/usr/bin/env bash
set -euo pipefail
here="$(cd -- "$(dirname -- "$0")" && pwd)"
[[ "$here" == /opt/url-shortener/deploy/oracle ]] || { echo 'Install at /opt/url-shortener or review unit paths first.' >&2; exit 1; }
sudo install -m 644 "$here"/systemd/url-shortener-* /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now url-shortener-backup.timer url-shortener-monitor.timer
