#!/usr/bin/env bash
set -euo pipefail
read -r -p "This will delete data/ and volumes/. Type DELETE to continue: " answer
if [[ "$answer" != "DELETE" ]]; then
  echo "Cancelled."
  exit 0
fi
docker compose -f vector-database.yml down -v || true
rm -rf data volumes
read -r -p "Also delete uploads/? Type DELETE_UPLOADS to confirm, or press Enter to keep: " upload_answer
if [[ "$upload_answer" == "DELETE_UPLOADS" ]]; then
  rm -rf uploads
fi
echo "Local data cleanup completed."
