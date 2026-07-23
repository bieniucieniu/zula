#!/usr/bin/env bash
set -eu

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$root"

echo "sync-schema: generating OpenAPI client from running server"
bun run gen:api

echo "sync-schema: formatting workspace"
bun run format

echo "sync-schema: building workspace packages"
bun run build:packages

echo "sync-schema: done"
