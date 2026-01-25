#!/usr/bin/env bash
set -euo pipefail

action="${1:-up}"
root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
compose_file="${root_dir}/compose.podman.yaml"

case "${action}" in
  up)
    podman compose -f "${compose_file}" up -d --build
    ;;
  down)
    podman compose -f "${compose_file}" down
    ;;
  *)
    echo "Usage: $(basename "$0") [up|down]" >&2
    exit 2
    ;;
esac
