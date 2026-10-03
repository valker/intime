[CmdletBinding()]
param(
    # Устройство выбирает пользователь: скрипт никогда не берёт первый подключённый телефон.
    [Parameter(Mandatory = $true)]
    [ValidatePattern('^[A-Za-z0-9_.:-]+$')]
    [string]$DeviceSerial,

    [string]$AdbPath = (Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe')
)

$ErrorActionPreference = 'Stop'
$packageName = 'com.vpe_soft.intime.intime'
if (-not (Test-Path -LiteralPath $AdbPath -PathType Leaf)) {
    throw "adb.exe not found: $AdbPath. Specify -AdbPath."
}

# pm path только читает пути APK установленного production-приложения.
# Это не копирование базы, установка, очистка данных или запуск приложения.
$packagePaths = @(& $AdbPath -s $DeviceSerial shell pm path $packageName)
if ($LASTEXITCODE -ne 0) { throw "Cannot read APK paths from device $DeviceSerial." }
$remotePaths = @($packagePaths | ForEach-Object {
    if ($_ -match '^package:(/\S+\.apk)\s*$') { $Matches[1] }
})
if ($remotePaths.Count -eq 0) { throw "No APK found for $packageName on $DeviceSerial." }

# Play может устанавливать base.apk вместе со split APK. Копируем весь найденный набор,
# чтобы позже можно было проверить подпись и подготовить установку на тестовом устройстве.
# Каждый запуск создаёт новую папку в игнорируемом каталоге проекта; старые файлы не заменяются.
$workspaceRoot = Split-Path -Parent $PSScriptRoot
$runName = (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N').Substring(0, 8)
$destination = Join-Path $workspaceRoot ('.test-tools\upgrade-apks\' + $runName)
$null = New-Item -ItemType Directory -Path $destination
$copied = @()
$names = [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::OrdinalIgnoreCase)
foreach ($remotePath in $remotePaths) {
    $name = [System.IO.Path]::GetFileName($remotePath)
    if ($name -notmatch '^[A-Za-z0-9_.-]+\.apk$' -or -not $names.Add($name)) {
        throw "Unexpected or duplicate APK name: $name"
    }
    $localPath = Join-Path $destination $name
    # adb pull читает APK с явно выбранного устройства, записывая копию только на компьютер.
    & $AdbPath -s $DeviceSerial pull $remotePath $localPath
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $localPath -PathType Leaf)) {
        throw "APK copy failed: $remotePath. Partial files remain in $destination."
    }
    $copied += [ordered]@{
        file = $name
        sourcePath = $remotePath
        sha256 = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash
    }
}

# Фиксируем происхождение и хеши файлов. Это хеши APK, а не SHA-256 сертификата подписи:
# сертификат и версию нужно отдельно извлечь из сохранённого APK через Android build-tools.
$metadata = [ordered]@{
    package = $packageName
    serial = $DeviceSerial
    copiedAt = (Get-Date).ToString('o')
    files = $copied
}
$metadata | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $destination 'source.json') -Encoding UTF8
Write-Output "APK copies saved: $destination"
