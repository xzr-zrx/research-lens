$answer = Read-Host "This will delete data/, volumes/ and generated files under uploads/. Type DELETE to continue"
if ($answer -ne "DELETE") {
  Write-Host "Cancelled."
  exit 0
}
docker compose -f vector-database.yml down -v
if (Test-Path "data") { Remove-Item "data" -Recurse -Force }
if (Test-Path "volumes") { Remove-Item "volumes" -Recurse -Force }
$uploadAnswer = Read-Host "Also delete uploads/? Type DELETE_UPLOADS to confirm, or press Enter to keep"
if ($uploadAnswer -eq "DELETE_UPLOADS" -and (Test-Path "uploads")) {
  Remove-Item "uploads" -Recurse -Force
}
Write-Host "Local data cleanup completed."
