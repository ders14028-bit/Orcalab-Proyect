# Construye las 5 imagenes de OrcaLab y las sube a ECR.
# Requisitos: Docker Desktop corriendo + credenciales AWS vigentes.
# Uso:  .\build-and-push.ps1   (desde terraform/scripts, o desde donde sea)

$ErrorActionPreference = "Stop"

$Region   = "us-east-1"
$Account  = (aws sts get-caller-identity --query Account --output text)
if ($LASTEXITCODE -ne 0) { throw "Credenciales AWS invalidas o expiradas - refresca el Learner Lab." }
$Registry = "$Account.dkr.ecr.$Region.amazonaws.com"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

Write-Host "Login a ECR $Registry..."
# El pipe va dentro de cmd /c: el pipeline de PowerShell 5.1 re-codifica el
# token y anade CRLF, lo que rompe el login con 400 Bad Request.
cmd /c "aws ecr get-login-password --region $Region | docker login --username AWS --password-stdin $Registry"
if ($LASTEXITCODE -ne 0) { throw "Fallo el login a ECR." }

$services = @("auth-service", "room-service", "realtime-service", "reporting-service", "vision-service")

foreach ($svc in $services) {
    $image = "$Registry/orcalab/${svc}:latest"
    Write-Host "`n=== Construyendo $svc ===" -ForegroundColor Cyan
    docker build -t $image (Join-Path $RepoRoot $svc)
    if ($LASTEXITCODE -ne 0) { throw "Fallo el build de $svc." }
    Write-Host "=== Subiendo $image ===" -ForegroundColor Cyan
    docker push $image
    if ($LASTEXITCODE -ne 0) { throw "Fallo el push de $svc." }
}

Write-Host "`nListo: las 5 imagenes estan en ECR." -ForegroundColor Green
