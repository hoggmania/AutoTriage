$ErrorActionPreference = "Stop"

param(
    [ValidateSet("up", "down")]
    [string]$Action = "up"
)

$composeFile = Join-Path $PSScriptRoot "..\\compose.podman.yaml"

if ($Action -eq "up") {
    podman compose -f $composeFile up -d --build
} else {
    podman compose -f $composeFile down
}
