# Loads backend/.env into the process environment and starts Spring Boot.
# Usage (from backend/):  .\run-with-env.ps1
# Does not print secret values.

$ErrorActionPreference = 'Stop'
$envFile = Join-Path $PSScriptRoot '.env'

if (-not (Test-Path $envFile)) {
    Write-Error "Missing $envFile - copy .env.example to .env and set DATABASE_PASSWORD."
}

Get-Content $envFile | ForEach-Object {
    $line = $_.Trim()
    if ($line -eq '' -or $line.StartsWith('#')) { return }
    $eq = $line.IndexOf('=')
    if ($eq -lt 1) { return }
    $name = $line.Substring(0, $eq).Trim()
    $value = $line.Substring($eq + 1)
    Set-Item -Path "Env:$name" -Value $value
}

$required = @('DATABASE_URL', 'DATABASE_USERNAME', 'DATABASE_PASSWORD')
foreach ($key in $required) {
    $val = [Environment]::GetEnvironmentVariable($key, 'Process')
    if ([string]::IsNullOrWhiteSpace($val) -or $val -eq 'YOUR_DATABASE_PASSWORD') {
        Write-Error "$key is missing or still a placeholder in .env"
    }
}

Write-Host 'Starting api-spy with DATABASE_URL host from .env (password not shown)...'
& (Join-Path $PSScriptRoot 'mvnw.cmd') spring-boot:run
