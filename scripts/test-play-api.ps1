<#
Проверяет доступ сервисного аккаунта к метаданным APK InTime через официальный API.
POST используется только для получения OAuth token; Play запрашивается через GET.
Не загружает AAB, не создаёт edits, не публикует и не обращается к устройствам.
JSON-ключ хранится вне проекта; его содержимое и токены не записываются в отчёт.
#>
[CmdletBinding()]
param(
    [string]$KeyFile = (Join-Path $env:USERPROFILE '.config\intime\play-service-account.json'),
    # Уже загруженная 26 позволяет проверить доступ до загрузки новой сборки.
    [ValidateRange(25,2100000000)][int]$VersionCode = 26,
    [string]$NodePath = (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
# Сохраняется только путь к ключу, не сам ключ. Явный KeyFile имеет приоритет,
# затем локальная ignored-конфигурация, затем стандартный путь в профиле.
$localConfig = Join-Path $root '.test-tools\play-api-local.json'
if (-not $PSBoundParameters.ContainsKey('KeyFile') -and (Test-Path -LiteralPath $localConfig -PathType Leaf)) {
    $config = Get-Content -LiteralPath $localConfig -Raw -Encoding UTF8 | ConvertFrom-Json
    if (-not ($config.keyFile -is [string]) -or [string]::IsNullOrWhiteSpace($config.keyFile)) { throw 'Local API config requires a keyFile path.' }
    $KeyFile = $config.keyFile
}
$run = Join-Path $root ('.test-tools\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-play-api-' + [guid]::NewGuid().ToString('N').Substring(0,8))
New-Item -ItemType Directory -Path $run | Out-Null
if (-not (Test-Path -LiteralPath $NodePath -PathType Leaf)) { throw 'Node.js missing; pass the existing NodePath, no automatic installation is performed.' }
# Скрипт Node печатает только безопасный summary; секреты не передаются через argv.
$previousPreference = $ErrorActionPreference
try {
    $ErrorActionPreference = 'Continue'
    $output = & $NodePath (Join-Path $PSScriptRoot 'play-api.mjs') '--key-file' $KeyFile '--version-code' "$VersionCode" '--report' (Join-Path $run 'summary.json') 2>&1
    $code = $LASTEXITCODE
} finally { $ErrorActionPreference = $previousPreference }
$output | Set-Content (Join-Path $run 'check.log') -Encoding UTF8
Write-Output "API check reports: $run"
if ($code -ne 0) {
    $output | Write-Output
    exit 1
}
$summary = Get-Content (Join-Path $run 'summary.json') -Raw | ConvertFrom-Json
if ($summary.status -ne 'PASSED') { throw 'Fresh API pass missing' }
Write-Output "PASSED: API access to InTime code $VersionCode; universal APK available=$($summary.universalApkAvailable)"
