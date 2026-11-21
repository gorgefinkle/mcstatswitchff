param(
    [Parameter(Mandatory=$true)]
    [string]$SourceZip,
    [string]$RepoDirectory = ".\\mcstatswitchff"
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path -LiteralPath $SourceZip -PathType Leaf)) {
    throw "Original R0.11 archive not found: $SourceZip"
}
$zip = (Resolve-Path -LiteralPath $SourceZip).Path
if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw "Git for Windows is required" }
if (-not (Get-Command python -ErrorAction SilentlyContinue)) { throw "Python 3 is required" }
if (-not (Test-Path -LiteralPath $RepoDirectory -PathType Container)) {
    git clone "https://github.com/gorgefinkle/mcstatswitchff.git" $RepoDirectory
    if ($LASTEXITCODE -ne 0) { throw "Git clone failed; verify GitHub authentication" }
}
Push-Location $RepoDirectory
try {
    git fetch origin
    if ($LASTEXITCODE -ne 0) { throw "Git fetch failed" }
    git checkout main
    if ($LASTEXITCODE -ne 0) { throw "Could not check out main" }
    if ((git status --porcelain)) { throw "Repository has local changes; refusing to overwrite or mix them." }
    git pull --ff-only origin main
    if ($LASTEXITCODE -ne 0) { throw "Could not fast-forward main" }
    python scripts/import-r011-missing.py $zip
    if ($LASTEXITCODE -ne 0) { throw "Historical import failed" }
    git add -A -- AUDIT_R0_*.md MANIFEST_PROJECT_SHA256.txt PROJECT_HANDOFF_README.md docs scripts
    if ($LASTEXITCODE -ne 0) { throw "Git staging failed" }
    git status --short
    git diff --cached --check
    if ($LASTEXITCODE -ne 0) { throw "Whitespace or patch check failed" }
    python scripts/self_audit.py
    if ($LASTEXITCODE -ne 0) { throw "R0.11 self-audit failed; changes left uncommitted for inspection" }
    $staged = git diff --cached --name-only
    if (-not $staged) { Write-Host "No missing files to import"; exit 0 }
    git commit -m "Restore historical R0.11 roadmap, test harnesses and audits"
    if ($LASTEXITCODE -ne 0) { throw "Commit failed" }
    git push origin main
    if ($LASTEXITCODE -ne 0) { throw "Push failed; local commit retained" }
    Write-Host "Original missing R0.11 files imported; GitHub Actions will verify both versions."
} finally {
    Pop-Location
}
