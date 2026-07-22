#!/usr/bin/env bash
# Print a primary LAN IPv4 for reaching this machine from phones / emulators.
# Prefer Wi-Fi (en0) on macOS; fall back to other common interfaces / Linux defaults.
set -euo pipefail

candidates=()

if command -v ipconfig >/dev/null 2>&1; then
  for iface in en0 en1 en2 en3 bridge0; do
    if addr="$(ipconfig getifaddr "$iface" 2>/dev/null)" && [ -n "$addr" ]; then
      candidates+=("$addr")
    fi
  done
fi

if command -v ip >/dev/null 2>&1; then
  while IFS= read -r addr; do
    [ -n "$addr" ] && candidates+=("$addr")
  done < <(ip -4 -o addr show scope global 2>/dev/null | awk '{print $4}' | cut -d/ -f1)
fi

if [ "${#candidates[@]}" -eq 0 ] && command -v hostname >/dev/null 2>&1; then
  # hostname -I is Linux-only; ignore failures on macOS/BSD.
  if addrs="$(hostname -I 2>/dev/null)"; then
    for addr in $addrs; do
      candidates+=("$addr")
    done
  fi
fi

for addr in "${candidates[@]+"${candidates[@]}"}"; do
  case "$addr" in
    127.*|0.*|169.254.*) continue ;;
    *)
      printf '%s\n' "$addr"
      exit 0
      ;;
  esac
done

exit 1
