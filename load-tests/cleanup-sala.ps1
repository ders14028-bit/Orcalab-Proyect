# Limpia la sala de prueba creada por k6-marcador-loadtest.js despues de una
# corrida contra produccion. La sala se borra via DELETE /api/salas/{id}
# (room-service), lo que dispara el evento "SalaEliminada" que a su vez hace
# que realtime-service borre en cascada canales, mensajes, marcadores, rutas y
# alertas de esa sala (ver SalaLimpiezaService). No hace falta tocar la base
# de datos directamente.
#
# Solo el lider de la sala puede eliminarla; loadtest1@orcalab.local es quien
# la crea en setup(), asi que es quien debe borrarla.
#
# Uso:
#   .\cleanup-sala.ps1 -SalaId 27

param(
    [Parameter(Mandatory = $true)]
    [int]$SalaId,

    [string]$BaseUrl = "https://orcalab.online",
    [string]$Email = "loadtest1@orcalab.local",
    [string]$Password = "LoadTest123!"
)

$ErrorActionPreference = "Stop"

$loginBody = @{ email = $Email; password = $Password } | ConvertTo-Json
$loginRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginBody -ContentType "application/json"
$token = $loginRes.token

if (-not $token) {
    Write-Error "No se pudo obtener token para $Email"
    exit 1
}

Write-Host "Eliminando sala $SalaId..."
Invoke-WebRequest -Uri "$BaseUrl/api/salas/$SalaId" -Method Delete -Headers @{ Authorization = "Bearer $token" } -UseBasicParsing | Out-Null
Write-Host "Sala $SalaId eliminada (marcadores, rutas, etc. de esa sala se borraron en cascada en realtime-service)."
