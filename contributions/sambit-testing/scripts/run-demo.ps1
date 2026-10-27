# One-command local demo for Windows PowerShell: tracker + 2 seeders + 4 leechers, then SHA-256 verification.
# Override anything with parameters, e.g.  .\scripts\run-demo.ps1 -TrackerPort 7000 -FileKb 8192
param(
    [int]$TrackerPort = 6969,
    [int]$SeedBasePort = 6101,
    [int]$LeechBasePort = 6201,
    [int]$FileKb = 4096,
    [int]$PieceKb = 64,
    [int]$UploadKbps = 1024,
    [string]$Strategy = "rarest-first",
    [string]$Work = "demo-work"
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
$cp = "target\classes"
if (-not (Test-Path $cp)) { & .\scripts\build.ps1 }
if (Test-Path $Work) { Remove-Item -Recurse -Force $Work }
New-Item -ItemType Directory -Force "$Work\source", "$Work\logs" | Out-Null

java -cp $cp edu.campustorrent.experiment.SampleFileGenerator --out "$Work\source\studyguide.bin" --size-kb $FileKb --seed 42
java -cp $cp edu.campustorrent.pieces.MakeManifest --file "$Work\source\studyguide.bin" --piece-size-kb $PieceKb --out "$Work\studyguide.manifest"

$all = @()
function Start-Java($name, $argList) {
    $p = Start-Process -FilePath java -ArgumentList $argList -PassThru -NoNewWindow `
         -RedirectStandardOutput "$Work\logs\$name.out.log" -RedirectStandardError "$Work\logs\$name.err.log"
    return $p
}
try {
    Write-Host "== Terminal 1: tracker on port $TrackerPort"
    $all += Start-Java "tracker" @("-cp", $cp, "edu.campustorrent.tracker.TrackerMain", "--port", $TrackerPort)
    Start-Sleep -Seconds 1

    foreach ($i in 1..2) {
        $port = $SeedBasePort + $i - 1
        Write-Host "== Terminal $($i+1): seeder S$i on port $port"
        $all += Start-Java "seeder$i" @("-cp", $cp, "edu.campustorrent.peer.PeerMain", "--role", "seeder", "--peer-id", "S$i",
            "--port", $port, "--tracker-port", $TrackerPort, "--manifest", "$Work\studyguide.manifest",
            "--file", "$Work\source\studyguide.bin", "--upload-kbps", $UploadKbps)
    }
    Start-Sleep -Seconds 1

    $leechers = @()
    foreach ($i in 1..4) {
        $port = $LeechBasePort + $i - 1
        Write-Host "== Terminal $($i+3): leecher L$i on port $port, output $Work\out\L$i"
        $p = Start-Java "leecher$i" @("-cp", $cp, "edu.campustorrent.peer.PeerMain", "--role", "leecher", "--peer-id", "L$i",
            "--port", $port, "--tracker-port", $TrackerPort, "--manifest", "$Work\studyguide.manifest",
            "--output-dir", "$Work\out\L$i", "--strategy", $Strategy, "--seed", $i, "--upload-kbps", $UploadKbps,
            "--linger-seconds", 5, "--timeout-seconds", 180)
        $leechers += $p
        $all += $p
    }

    Write-Host "== waiting for leechers (logs in $Work\logs)"
    $fail = $false
    foreach ($p in $leechers) { $p.WaitForExit(); if ($p.ExitCode -ne 0) { $fail = $true } }
    Get-ChildItem "$Work\logs\leecher*.out.log" | ForEach-Object { Select-String -Path $_.FullName -Pattern "SUMMARY" } | ForEach-Object { $_.Line }

    Write-Host "== verifying downloads"
    $files = 1..4 | ForEach-Object { "$Work\out\L$_\studyguide.bin" }
    & .\scripts\verify-downloads.ps1 -Original "$Work\source\studyguide.bin" -Downloads $files
    if ($LASTEXITCODE -ne 0) { $fail = $true }
    if ($fail) { Write-Host "DEMO FAILED (see $Work\logs)"; exit 1 } else { Write-Host "DEMO SUCCESS" }
} finally {
    foreach ($p in $all) { if (-not $p.HasExited) { Stop-Process -Id $p.Id -Force -ErrorAction SilentlyContinue } }
}
