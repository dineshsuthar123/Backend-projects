#!/usr/bin/env bash
# Add only web ingress, preserving existing SSH rules, chains, and SupplyPrint state.
set -euo pipefail
if command -v ufw >/dev/null && sudo ufw status | grep -q 'Status: active'; then
  sudo ufw allow 80/tcp
  sudo ufw allow 443/tcp
else
  for port in 80 443; do
    sudo iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null || \
      sudo iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
  done
  if command -v netfilter-persistent >/dev/null; then sudo netfilter-persistent save; fi
fi
echo 'Web ingress added. SSH rules preserved. Verify OCI ingress and host rules before deployment.'
