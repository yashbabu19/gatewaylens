#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONFIG_PATH="${1:-examples/kong-declarative.yaml}"

if ! command -v deck >/dev/null 2>&1; then
  cat >&2 <<'MSG'
GatewayLens uses decK for authoritative offline schema validation of Kong state files.
Install decK first: https://developer.konghq.com/deck/
Then rerun: scripts/validate-kong-with-deck.sh [path/to/kong.yaml]
MSG
  exit 127
fi

if [[ "$CONFIG_PATH" != /* ]]; then
  CONFIG_PATH="$ROOT_DIR/$CONFIG_PATH"
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "Kong configuration file not found: $CONFIG_PATH" >&2
  exit 2
fi

# Offline schema and relationship validation only; no live gateway is contacted.
deck file validate "$CONFIG_PATH"
