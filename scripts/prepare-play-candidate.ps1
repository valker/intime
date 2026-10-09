<#
Загружает точный проверенный AAB 27 во временный edit и скачивает Play APK.
Каналы/выпуски не меняются, commit/publish не вызываются. Edit имеет срок жизни;
пока он используется, изменения приложения в Console могут его инвалидировать.
Download — повтор только GET после уже подтверждённой загрузки, без нового edit.
Каждый запуск сохраняет checkpoint и итог под .test-tools/runs.
#>
[CmdletBinding()]
param(
    [ValidateSet('prepare','download')][string]$Mode = 'prepare',
    [string]$KeyFile,
    [string]$NodePath = (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if ([string]::IsNullOrWhiteSpace($KeyFile)) {
    $config = Get-Content (Join-Path $root '.test-tools\play-api-local.json') -Raw | ConvertFrom-Json
    $KeyFile = $config.keyFile
}
$run = Join-Path $root ('.test-tools\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-play27-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $run | Out-Null
$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $output = & $NodePath (Join-Path $PSScriptRoot 'prepare-play-candidate.mjs') $Mode $KeyFile $run 2>&1
    $code = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
$output | Set-Content (Join-Path $run 'api.log') -Encoding UTF8
Write-Output "Play27 reports: $run"
if ($code -ne 0) { $output | Write-Output; exit 1 }
$summary = Get-Content (Join-Path $run 'summary.json') -Raw | ConvertFrom-Json
if ($summary.status -ne 'DOWNLOADED_NOT_VERIFIED') { throw 'Downloaded artifact missing' }
# API certificate metadata не заменяет настоящую проверку скачанного APK.
& powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'verify-play-apk.ps1') -ApkPath $summary.apk -ExpectedVersionCode 27 -ExpectedVersionName '2.0.0-rc3'
if ($LASTEXITCODE -ne 0) { throw 'Downloaded Play APK verification failed; do not install.' }
$summary.status = 'APK_VERIFIED'
$summary | ConvertTo-Json -Depth 8 | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
Write-Output "APK_VERIFIED: $($summary.apk)"
