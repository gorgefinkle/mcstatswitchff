param(
    [Parameter(Mandatory=$true)]
    [string]$SourceZip,
    [string]$Repository = 'https://github.com/gorgefinkle/mcstatswitchff.git'
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $SourceZip)) { throw "Source ZIP not found: $SourceZip" }
if (!(Get-Command git -ErrorAction SilentlyContinue)) { throw 'Install Git for Windows first.' }
$sourceAbsolute = (Resolve-Path -LiteralPath $SourceZip).Path
$workspace = Join-Path ([System.IO.Path]::GetTempPath()) ('ff-import-' + [guid]::NewGuid().ToString('N'))
$checkout = Join-Path $workspace 'repo'
$unpacked = Join-Path $workspace 'unpacked'
New-Item -ItemType Directory -Path $workspace,$unpacked -Force | Out-Null
try {
    git clone $Repository $checkout
    if ($LASTEXITCODE -ne 0) { throw 'Git clone failed; authenticate to GitHub.' }
    Expand-Archive -LiteralPath $sourceAbsolute -DestinationPath $unpacked -Force
    if (!(Test-Path (Join-Path $unpacked 'build.gradle')) -or !(Test-Path (Join-Path $unpacked 'src/main/java/dev/freakyfriday/FreakyFriday.java'))) {
        throw 'Wrong ZIP: expected FreakyFriday_R0_11_Full_Source.zip.'
    }
    Get-ChildItem -LiteralPath $unpacked -Force | Where-Object {
        $_.Name -notin @('.gitignore', 'MANIFEST_PROJECT_SHA256.txt', 'logs')
    } | ForEach-Object { Copy-Item -LiteralPath $_.FullName -Destination $checkout -Recurse -Force }
    Push-Location $checkout
    try {
        git add -A
        git status --short
        git commit -m 'Import complete Freaky Friday R0.11 source and tests'
        if ($LASTEXITCODE -ne 0) { throw 'Git commit failed; configure Git name and email.' }
        git push origin main
        if ($LASTEXITCODE -ne 0) { throw 'Git push failed; check GitHub authentication.' }
    } finally { Pop-Location }
    Write-Host 'R0.11 source uploaded. Check the GitHub Actions tab.' -ForegroundColor Green
} finally {
    Remove-Item -LiteralPath $workspace -Recurse -Force -ErrorAction SilentlyContinue
}