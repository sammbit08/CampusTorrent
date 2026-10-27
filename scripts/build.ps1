# Builds target\classes with Maven if available, otherwise with javac (JDK 17+; 21 preferred).
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
if (Get-Command mvn -ErrorAction SilentlyContinue) {
    mvn -q -DskipTests compile
} elseif (Get-Command javac -ErrorAction SilentlyContinue) {
    if (Test-Path target\classes) { Remove-Item -Recurse -Force target\classes }
    New-Item -ItemType Directory -Force target\classes | Out-Null
    $files = Get-ChildItem -Recurse src\main -Filter *.java | ForEach-Object { $_.FullName }
    javac -d target\classes $files
} else {
    throw "Neither mvn nor javac found. Install a JDK (21 preferred) and Maven."
}
Write-Host "Build OK -> target\classes"
