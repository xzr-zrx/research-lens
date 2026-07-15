#!/usr/bin/env bash
set -euo pipefail
echo "Starting Milvus standalone infrastructure..."
docker compose -f vector-database.yml up -d
echo "Milvus is starting. Run: docker compose -f vector-database.yml ps"
