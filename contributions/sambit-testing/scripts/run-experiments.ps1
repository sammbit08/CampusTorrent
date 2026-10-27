# Runs experiments and writes results\experiments.csv. Usage: .\scripts\run-experiments.ps1 [-Experiment all|strategy|seeders|freerider]
param(
    [string]$Experiment = "all", [int]$Runs = 3, [int]$Leechers = 5, [int]$FileKb = 2048,
    [int]$PieceKb = 64, [int]$UploadKbps = 1024, [string]$Out = "results\experiments.csv"
)
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (-not (Test-Path target\classes)) { & .\scripts\build.ps1 }
java -cp target\classes edu.campustorrent.experiment.ExperimentRunner --experiment $Experiment --runs $Runs `
    --leechers $Leechers --file-kb $FileKb --piece-kb $PieceKb --upload-kbps $UploadKbps --out $Out
Write-Host "Plot with: python scripts\plot_results.py --csv $Out --out results\plots"
