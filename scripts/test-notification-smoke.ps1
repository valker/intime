<#
Дополнительная проверка API 33/35 на уже запущенном проектном AVD.
Сначала выполнить scripts/test.ps1 -Mode device -TestApi 33 -Offline -KeepEmulator.
Для API 35 укажите -TestApi 35.
Затем вызвать этот скрипт с тем же -TestApi; по умолчанию оба используют API 35.
Скрипт устанавливает собранные .dev APK, проверяет отказ в разрешении и доставку,
создаёт синтетическую reboot-фикстуру, перезагружает только выбранный Intime_Test_API33/35
и проверяет восстановление. Личные AVD и пользовательские main*.db не используются.
#>
[CmdletBinding()]
param(
    [ValidateSet(33, 35)][int]$TestApi = 35,
    [ValidateRange(1, 1200)][int]$BootTimeoutSeconds = 300
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-notification-smoke')
New-Item -ItemType Directory -Path $run -Force | Out-Null
$adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'
$serial = if ($TestApi -eq 33) { 'emulator-5582' } else { 'emulator-5580' }
$package = 'com.vpe_soft.intime.intime.dev'
$runner = "$package.test/androidx.test.runner.AndroidJUnitRunner"
$lock = $null
$summary = @{ status = 'RUNNING'; passedPhases = @(); reports = $run; serial = $serial; testApi = $TestApi }

# Сохраняем вывод каждой adb-команды и проверяем её код завершения.
function Invoke-TestAdb([string[]]$Arguments, [string]$Name) {
    $output = & $adb -s $serial @Arguments 2>&1
    $code = $LASTEXITCODE
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    $text | Set-Content (Join-Path $run "$Name.log") -Encoding UTF8
    if ($code -ne 0) { throw "adb failed ($code): $Name. See $run" }
    return $text
}

# Instrumentation может вернуть adb exit=0 даже при падении теста: требуем OK (1 test).
function Invoke-TestPhase([string]$ClassMethod, [string]$Name) {
    $output = Invoke-TestAdb @('shell', 'am', 'instrument', '-w', '-r', '-e', 'class', $ClassMethod, $runner) $Name
    if ($output -notmatch 'OK \(1 test\)' -or
            $output -match 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_STATUS_CODE:\s*-(3|4)') {
        throw "Instrumentation phase failed: $Name. See $run"
    }
    $summary.passedPhases += $Name
    Write-Output "PASSED: $Name"
}

# Берём только действующий RTC_WAKEUP, а не историю отменённых будильников в dumpsys.
function Get-TaskAlarmDeadline([string]$Dump) {
    $pattern = '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\.dev\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime\.dev/com\.vpe_soft\.intime\.intime\.receiver\.AlarmReceiver\s*$'
    $match = [regex]::Match($Dump, $pattern)
    if ($match.Success) { return $match.Groups['deadline'].Value }
    return ''
}

try {
    # Блокируем параллельные тесты и проверяем сохранённую принадлежность эмулятора проекту.
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $state = Get-Content (Join-Path $work 'emulator.json') -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.processId -ErrorAction Stop
    if ($state.serial -ne $serial -or $process.StartTime.ToUniversalTime().ToString('o') -ne $state.started) {
        throw 'Emulator ownership record is stale.'
    }
    $avd = Invoke-TestAdb @('emu', 'avd', 'name') 'avd-name'
    if ($avd.Split("`n")[0].Trim() -ne "Intime_Test_API$TestApi") { throw 'Refusing another AVD.' }
    if ((Invoke-TestAdb @('shell', 'getprop', 'ro.build.version.sdk') 'api').Trim() -ne "$TestApi") {
        throw 'Emulator API does not match TestApi.'
    }

    # Gradle удаляет тестовые APK после suite; install -r сохраняет данные, если APK ещё установлен.
    Invoke-TestAdb @('install', '-r', (Join-Path $root 'app\build\outputs\apk\debug\app-debug.apk')) 'install-app' | Out-Null
    Invoke-TestAdb @('install', '-r', (Join-Path $root 'app\build\outputs\apk\androidTest\debug\app-debug-androidTest.apk')) 'install-tests' | Out-Null

    # Отзываем POST_NOTIFICATIONS до старта instrumentation: отзыв может завершить процесс приложения.
    Invoke-TestAdb @('shell', 'pm', 'revoke', $package, 'android.permission.POST_NOTIFICATIONS') 'revoke-permission' | Out-Null
    Invoke-TestPhase 'com.vpe_soft.intime.intime.receiver.NotificationDeliveryTest#deniedRuntimePermission_doesNotMarkTask' 'permission-denied'
    Invoke-TestPhase 'com.vpe_soft.intime.intime.receiver.NotificationDeliveryTest#platformAlarm_postsNotificationAndAcknowledgesTask' 'platform-delivery-ack'
    # Одной instrumentation недостаточно для первого запуска установленного APK:
    # stopped/notLaunched запрещают BOOT_COMPLETED. Открываем launcher и уходим домой
    # до подготовки фикстуры, имитируя обычное использование приложения перед reboot.
    Invoke-TestAdb @('shell', 'am', 'start', '-W', '-n', "$package/com.vpe_soft.intime.intime.activity.MainActivityV2") 'first-launch' | Out-Null
    Invoke-TestAdb @('shell', 'input', 'keyevent', '3') 'background-app' | Out-Null
    Invoke-TestAdb @('shell', 'dumpsys', 'package', $package) 'package-before-reboot' | Out-Null
    Invoke-TestPhase 'com.vpe_soft.intime.intime.receiver.RebootRecoveryTest#prepareForReboot' 'prepare-reboot'

    # BOOT_COUNT защищает от ложной готовности, если старый sys.boot_completed ещё равен 1.
    $previousBoot = [int](Invoke-TestAdb @('shell', 'settings', 'get', 'global', 'boot_count') 'boot-count-before').Trim()
    $summary.bootCountBefore = $previousBoot
    $alarmsBefore = Invoke-TestAdb @('shell', 'dumpsys', 'alarm') 'alarms-before'
    $expectedDeadline = Get-TaskAlarmDeadline $alarmsBefore
    if (-not $expectedDeadline) { throw 'Prepared AlarmReceiver alarm is missing.' }
    Invoke-TestAdb @('reboot') 'reboot' | Out-Null
    Write-Output "Waiting for real reboot of $serial..."
    $deadline = (Get-Date).AddSeconds($BootTimeoutSeconds)
    $booted = $false
    while ((Get-Date) -lt $deadline) {
        try {
            $bootFlag = (Invoke-TestAdb @('shell', 'getprop', 'sys.boot_completed') 'boot-poll').Trim()
            $currentBoot = [int](Invoke-TestAdb @('shell', 'settings', 'get', 'global', 'boot_count') 'boot-count-after').Trim()
            $booted = $bootFlag -eq '1' -and $currentBoot -gt $previousBoot
        } catch { $booted = $false }
        if ($booted) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $booted) { throw 'TIMEOUT: emulator reboot did not finish.' }
    $summary.bootCountAfter = $currentBoot

    # sys.boot_completed не означает доставку BOOT_COMPLETED всем приложениям.
    # Не запускаем instrumentation раньше receiver: оно может подавить ещё ожидающий broadcast.
    $receiverDeadline = (Get-Date).AddSeconds(180)
    $restored = $false
    while ((Get-Date) -lt $receiverDeadline) {
        $alarmsAfter = Invoke-TestAdb @('shell', 'dumpsys', 'alarm') 'alarms-after'
        $restored = (Get-TaskAlarmDeadline $alarmsAfter) -eq $expectedDeadline
        if ($restored) { break }
        Start-Sleep -Seconds 2
    }
    if (-not $restored) { throw 'BootReceiver did not restore the same alarm deadline.' }
    $summary.restoredAlarmDeadline = $expectedDeadline
    Write-Output 'PASSED: reboot restored AlarmReceiver deadline'
    # Снимки сохраняются до очистки фикстуры; сверены реальный receiver и точный срок.
    Invoke-TestAdb @('shell', 'dumpsys', 'notification', '--noredact') 'notifications-after' | Out-Null
    Invoke-TestPhase 'com.vpe_soft.intime.intime.receiver.RebootRecoveryTest#verifyAfterReboot' 'verify-reboot'
    $summary.status = 'PASSED'
} catch {
    $summary.status = 'FAILED'
    $summary.message = $_.Exception.Message
    Write-Warning $summary.message
} finally {
    if ($lock) { $lock.Dispose() }
    $summary | ConvertTo-Json | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
}
Write-Output "$($summary.status). Reports: $run"
if ($summary.status -ne 'PASSED') { exit 1 }
