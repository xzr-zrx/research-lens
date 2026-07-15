$ErrorActionPreference = "Stop"
Write-Host "Starting Milvus standalone infrastructure..."
docker compose -f vector-database.yml up -d
Write-Host "Milvus is starting. Run 'docker compose -f vector-database.yml ps' to check status."
