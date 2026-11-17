#!/usr/bin/env bash
# Only for the reviewed shared Ubuntu host. Does not modify OCI, SSH or firewall rules.
set -euo pipefail
[[ "$(uname -m)" == aarch64 ]] || { echo 'Approved host must be ARM64.' >&2; exit 1; }
source /etc/os-release
[[ "$ID" == ubuntu ]] || { echo 'Ubuntu is required.' >&2; exit 1; }
if ! command -v docker >/dev/null; then
  sudo apt-get update
  sudo apt-get install -y ca-certificates curl gnupg
  sudo install -m 0755 -d /etc/apt/keyrings
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
  printf 'deb [arch=arm64 signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu %s stable\n' "$VERSION_CODENAME" | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
  sudo apt-get update
  sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin python3
fi
sudo systemctl enable --now docker
docker compose version
if ! docker network inspect portfolio-edge >/dev/null 2>&1; then
  docker network create --subnet 172.30.10.0/24 --label portfolio.shared=true portfolio-edge
fi
actual="$(docker network inspect -f '{{(index .IPAM.Config 0).Subnet}}' portfolio-edge)"
[[ "$actual" == 172.30.10.0/24 ]] || { echo 'Existing shared network differs; review before changing it.' >&2; exit 1; }
echo 'Docker and shared proxy network ready. Preserve existing SSH/firewall rules; see OPERATIONS.md.'
