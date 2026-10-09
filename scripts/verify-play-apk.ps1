<#
Проверка скачанного из Play Console универсального APK до любой установки.
Читает только локальный файл: не использует adb, телефон, базы или keystore.
PASSED подтверждает метаданные и известный сертификат, но не миграцию/доставку
уведомлений, совместимость ABI или сохранение данных при install -r.
Историю ротации ключей этот консервативный контроль не анализирует.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    # Код 25 разрешён для проверки скачанной старой Play-версии, а не новой сборки.
    [Parameter(Mandatory = $true)][ValidateRange(25, 2100000000)][int]$ExpectedVersionCode,
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[0-9]+\.[0-9]+\.[0-9]+(?:-[A-Za-z0-9.]+)?$')][string]$ExpectedVersionName,
    [string]$BuildToolsPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\build-tools\35.0.0'),
    [string]$JdkPath = 'C:\Program Files\Java\jdk-17'
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# На входе — вывод успешно завершённых aapt/apksigner. Проверяем точный пакет,
# код и имя, запрет debuggable и ровно одного подписанта с известным Play SHA-256.
# Подпись Upload key намеренно не подходит. Валидность самой подписи обеспечивает
# внешний apksigner verify; синтетический текст не является доказательством подписи.
function Assert-PlayApkMetadata([string]$Badging, [string]$Certificate, [int]$Code, [string]$VersionName) {
    $identity = "(?m)^package: name='com\.vpe_soft\.intime\.intime' versionCode='$Code' versionName='$([regex]::Escape($VersionName))'(?:\s|$)"
    if ($Badging -notmatch $identity -or $Badging -match '(?m)^application-debuggable(?:\s|$)') {
        throw 'Unexpected package/version or debuggable APK.'
    }
    $signers = [regex]::Matches($Certificate, '(?m)^Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})\s*$')
    $expected = '4a37905063f6896ec53f3aee47757c0541ce8c5094d12f9bfb21a7348f2ef17c'
    if ($signers.Count -ne 1 -or $signers[0].Groups[1].Value.ToLowerInvariant() -ne $expected) {
        throw 'Expected Google Play app signing certificate; upload-signed APK is not accepted.'
    }
}

$root = Split-Path $PSScriptRoot -Parent
$run = Join-Path $root ('.test-tools\runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-play-apk-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
$null = New-Item -ItemType Directory -Path $run
$summary = [ordered]@{ status = 'RUNNING'; expectedVersionCode = $ExpectedVersionCode; expectedVersionName = $ExpectedVersionName; phoneUsed = $false; installed = $false; reports = $run }
$oldJavaHome = $env:JAVA_HOME
try {
    # Проверяем инструменты и вход до запуска. APK не копируем и не переподписываем.
    $apk = (Resolve-Path -LiteralPath $ApkPath).Path
    $aapt = Join-Path $BuildToolsPath 'aapt.exe'
    $signer = Join-Path $BuildToolsPath 'apksigner.bat'
    foreach ($file in @($apk, $aapt, $signer, (Join-Path $JdkPath 'bin\java.exe'))) {
        if (-not (Test-Path -LiteralPath $file -PathType Leaf)) { throw "File not found: $file" }
    }
    $summary.apk = $apk
    $summary.sha256 = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash
    $env:JAVA_HOME = $JdkPath
    $badging = (& $aapt dump badging $apk 2>&1 | Out-String)
    $aaptExit = $LASTEXITCODE
    $badging | Set-Content -LiteralPath (Join-Path $run 'badging.log') -Encoding UTF8
    if ($aaptExit -ne 0) { throw "aapt failed: $aaptExit" }
    $certificate = (& $signer verify --verbose --print-certs $apk 2>&1 | Out-String)
    $signerExit = $LASTEXITCODE
    $certificate | Set-Content -LiteralPath (Join-Path $run 'certificate.log') -Encoding UTF8
    if ($signerExit -ne 0) { throw "apksigner verification failed: $signerExit" }
    Assert-PlayApkMetadata $badging $certificate $ExpectedVersionCode $ExpectedVersionName
    if ((Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash -ne $summary.sha256) { throw 'APK changed during verification.' }
    $summary.status = 'PASSED'
    Write-Output "PASSED: Play APK metadata/signature verified. Report: $run"
} catch {
    $summary.status = 'FAILED'
    $summary.error = $_.Exception.Message
    throw
} finally {
    $env:JAVA_HOME = $oldJavaHome
    $summary | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $run 'summary.json') -Encoding UTF8
}
