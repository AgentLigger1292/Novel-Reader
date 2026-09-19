# verify.ps1 — Windows twin of scripts/verify.sh (contract in AGENTS.md).
# Prints the same machine-parseable block; exit codes 0 pass / 1 tests / 2 compile.
param([switch]$Quick)
$ErrorActionPreference = 'Continue'
Set-Location (Join-Path $PSScriptRoot '..')

$gradle = if ($IsWindows -or $env:OS -eq 'Windows_NT') { '.\gradlew.bat' } else { './gradlew' }
$testResultsDir = 'app/build/test-results/testDebugUnitTest'
$testReport = 'app/build/reports/tests/testDebugUnitTest/index.html'

function Fail-Compile {
  Write-Output 'STATUS: error'
  Write-Output 'SUMMARY: compilation/packaging failed (see gradle output above)'
  Write-Output 'NEXT: fix the errors listed above, rerun the verify script'
  exit 2
}

function Fail-Tests {
  $classes = Get-ChildItem "$testResultsDir/TEST-*.xml" -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -match 'TEST-(.*)\.xml' -and
      (Get-Content $_.FullName -Raw) -match 'failures="[1-9]|errors="[1-9]' } |
    ForEach-Object { $_.Name -replace '^TEST-(.*)\.xml$', '$1' } |
    Select-Object -First 3
  Write-Output 'STATUS: error'
  Write-Output 'SUMMARY: unit tests failed'
  if ($classes) { Write-Output ("NEXT: inspect failing test class(es): " + ($classes -join ' ')) }
  else { Write-Output "NEXT: open $testReport for the failing tests" }
  Write-Output "REPORT: $testReport"
  exit 1
}

Write-Output '[verify] compiling...'
& $gradle :app:compileDebugKotlin --console=plain -q
if ($LASTEXITCODE -ne 0) { Fail-Compile }

Write-Output '[verify] unit tests...'
& $gradle :app:testDebugUnitTest --console=plain
if ($LASTEXITCODE -ne 0) { Fail-Tests }

if ($Quick) {
  Write-Output 'STATUS: success'
  Write-Output 'SUMMARY: compile + unit tests passed (quick mode, no APK packaged)'
  exit 0
}

Write-Output '[verify] packaging APK...'
& $gradle :app:assembleDebug --console=plain -q
if ($LASTEXITCODE -ne 0) { Fail-Compile }

$tests = 0
Get-ChildItem "$testResultsDir/TEST-*.xml" -ErrorAction SilentlyContinue | ForEach-Object {
  if ((Get-Content $_.FullName -Raw) -match 'tests="(\d+)"') { $tests += [int]$Matches[1] }
}
Write-Output 'STATUS: success'
Write-Output "SUMMARY: $tests unit tests passed; APK: app/build/outputs/apk/debug/app-debug.apk"
Write-Output "REPORT: $testReport"
exit 0
