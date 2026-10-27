# Usage: .\scripts\verify-downloads.ps1 -Original <file> -Downloads <file1>,<file2>,...
# Exit code 0 only if every downloaded file has the same SHA-256 as the original.
param(
    [Parameter(Mandatory = $true)][string]$Original,
    [Parameter(Mandatory = $true)][string[]]$Downloads
)
$want = (Get-FileHash -Algorithm SHA256 -Path $Original).Hash.ToLower()
Write-Host "original  $want  $Original"
$bad = $false
foreach ($f in $Downloads) {
    if (-not (Test-Path $f)) { Write-Host "MISSING   $f"; $bad = $true; continue }
    $got = (Get-FileHash -Algorithm SHA256 -Path $f).Hash.ToLower()
    if ($got -eq $want) { Write-Host "MATCH     $got  $f" } else { Write-Host "MISMATCH  $got  $f"; $bad = $true }
}
if ($bad) { exit 1 } else { exit 0 }
