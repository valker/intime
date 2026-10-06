<#
Проверяет уже собранный production release APK через UI на проектном AVD API 24/31/33/35/36.
API 24 пока поддерживает только RebootOnly (CRUD/JSON/reboot), без notification phases.
Перед запуском: scripts/test.ps1 -Mode device -TestApi <24, 31, 33, 35 или 36> -Offline -KeepEmulator.
Одинаковый TestApi нужно передать обоим скриптам; по умолчанию здесь используется 36.
APK должен быть подписан подтверждённым upload key; скрипт не публикует его в Play.
Используются только новые синтетические задачи. Если production-пакет уже установлен,
скрипт отказывается продолжать; данные существующей установки не очищаются.
После проверки удаляется только APK, установленный этим запуском. Эмулятор остаётся работать.
PeriodicOnly (API 31/33/35/36) проходит подготовку/boot/первый alarm и ждёт тихий
повтор после am kill фонового процесса. Обычный полный режим сохраняет 14 фаз;
periodic-режим содержит 10 фаз (11 при PeriodicRepeats=2) и не меняет batching/период/часы Android.
InexactOnly (API 36) содержит 11 фаз: подготовка, доставка fallback и ACK/restart.
InexactOnly + DozeOnly содержит 12 фаз: дополнительно подтверждается deep IDLE.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$ApkPath,
    # Выбор ограничен проектными AVD; произвольный serial принимать небезопасно.
    [ValidateSet(24, 31, 33, 35, 36)][int]$TestApi = 36,
    # Точечный режим проходит подготовку/JSON и повторяет reboot без минутных уведомлений.
    [switch]$RebootOnly,
    # Только доставка в deep idle и ACK после подготовки/reboot; без фаз запрета.
    [switch]$DozeOnly,
    # На API 36 запрещаем exact только своей установке и проверяем inexact delivery/ACK.
    [switch]$InexactOnly,
    # После реального первого alarm ждём тихого повторного worker в release/R8.
    # Никакого ручного job run, ускорения часов/периода или изменения batching.
    [switch]$PeriodicOnly,
    # Общий срок ожидания всех повторов, а не отдельный срок на каждый повтор.
    [ValidateRange(16, 75)][int]$PeriodicWaitMinutes = 65,
    # Два повтора проверяют также следующий естественный период после первого worker.
    [ValidateRange(1, 2)][int]$PeriodicRepeats = 1,
    [ValidateRange(1, 10)][int]$RebootRepeats = 1,
    # Ноль позволяет проверить гипотезу о задержке записи stopped после force-stop.
    [ValidateRange(0, 60)][int]$RebootSettleSeconds = 15
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$work = Join-Path $root '.test-tools'
$run = Join-Path $work ('runs\' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-release-smoke')
New-Item -ItemType Directory -Path $run -Force | Out-Null
$sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
$adb = Join-Path $sdk 'platform-tools\adb.exe'
# Каждому разрешённому API соответствует только закреплённый проектный порт.
$serial = switch ($TestApi) { 24 { 'emulator-5588' }; 31 { 'emulator-5586' }; 33 { 'emulator-5582' }; 35 { 'emulator-5580' }; 36 { 'emulator-5584' } }
$package = 'com.vpe_soft.intime.intime'
$installedHere = $false
$probePushed = $false
$dozeChanged = $false
$dozeDeepWasEnabled = $null
$backupDevicePath = $null
$oldJavaHome = $env:JAVA_HOME
$lock = $null
$commandNumber = 0
$summary = @{ status = 'RUNNING'; passedPhases = @(); reports = $run; serial = $serial; testApi = $TestApi; rebootOnly = [bool]$RebootOnly; rebootResults = @(); rebootSettleSeconds = $RebootSettleSeconds }
if (-not $RebootOnly -and $RebootRepeats -ne 1) { throw 'RebootRepeats requires RebootOnly' }
if ($RebootOnly -and $DozeOnly) { throw 'RebootOnly and DozeOnly are mutually exclusive' }
$summary.dozeOnly = [bool]$DozeOnly
$summary.inexactOnly = [bool]$InexactOnly
if ($InexactOnly -and ($RebootOnly -or $PeriodicOnly -or $TestApi -ne 36)) { throw 'InexactOnly requires API 36 and can only be combined with DozeOnly.' }
$summary.periodicOnly = [bool]$PeriodicOnly
if (-not $PeriodicOnly -and $PeriodicRepeats -ne 1) { throw 'PeriodicRepeats requires PeriodicOnly' }
$summary.periodicRepeats = $PeriodicRepeats
if ($PeriodicOnly -and ($RebootOnly -or $DozeOnly -or $TestApi -notin @(31, 33, 35, 36))) { throw 'PeriodicOnly requires API 31/33/35/36 and cannot be combined with other modes.' }
# API 24 не имеет notification channels и новых Settings intents. Пока эти фазы
# не адаптированы, отказываем до установки APK вместо ложного полного smoke.
if ($TestApi -eq 24 -and -not $RebootOnly) { throw 'API 24 currently requires RebootOnly; notification phases are not supported yet.' }

# SDK-компиляторы тоже могут писать предупреждения в stderr с exit 0.
# Сохраняем оба потока и определяем результат по коду завершения.
function Invoke-ReleaseTool([string]$Executable, [string[]]$Arguments, [string]$LogName) {
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $Executable @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    Set-Content (Join-Path $run $LogName) $text -Encoding UTF8
    if ($code -ne 0) { throw "SDK tool failed ($code): $LogName" }
    return $text
}

# Каждый вызов адресован одному AVD, сохраняет вывод и проверяет код завершения.
function Invoke-ReleaseAdb([string[]]$Arguments) {
    $script:commandNumber++
    # PowerShell 5.1 превращает stderr в ErrorRecord даже при exit 0 (adb pull
    # пишет туда обычный прогресс). Временно собираем оба потока, затем оцениваем
    # именно exit code; ненулевой результат по-прежнему останавливает фазу.
    $previousPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        $output = & $adb -s $serial @Arguments 2>&1
        $code = $LASTEXITCODE
    } finally { $ErrorActionPreference = $previousPreference }
    $text = ($output | ForEach-Object { "$_" }) -join "`n"
    Set-Content (Join-Path $run ("adb-{0:D3}.log" -f $script:commandNumber)) $text -Encoding UTF8
    if ($code -ne 0) { throw "adb failed ($code): $($Arguments -join ' ')" }
    return $text
}

# UIAutomator читает реальные элементы установленного APK, не обращаясь к его Java-классам.
function Read-ReleaseUi {
    # Android 16 вынес legacy android.test в отдельные framework JAR; явно
    # добавляем их в classpath shell-runner, не в APK приложения.
    $probe = Invoke-ReleaseAdb @('shell', 'uiautomator', 'runtest', '/system/framework/android.test.runner.jar', '/system/framework/android.test.base.jar', '/data/local/tmp/intime-release-probe.jar', '-c', 'intime.smoke.ReleaseHierarchyProbe')
    if ($probe -notmatch 'OK \(1 test\)' -or $probe -notmatch 'INTIME_FRESH_HIERARCHY_READY' -or $probe -match 'FAILURES!!!|INSTRUMENTATION_FAILED|Test run aborted|shortMsg=') { throw 'Fresh UI hierarchy probe failed' }
    $path = Join-Path $run ("ui-{0:D3}.xml" -f $script:commandNumber)
    Invoke-ReleaseAdb @('pull', '/data/local/tmp/intime-release-ui.xml', $path) | Out-Null
    return [xml](Get-Content $path -Raw -Encoding UTF8)
}
# После Home команда раскрытия шторки может потеряться в переходе. Подтверждаем
# реальный контейнер уведомлений по свежему XML, иначе отсутствие описания ничего
# не доказывает. Повторяем раскрытие ограниченно, только на проектном AVD.
function Read-ReleaseShade {
    $limit = (Get-Date).AddSeconds(20)
    do {
        Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
        Start-Sleep -Milliseconds 500
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='com.android.systemui:id/notification_stack_scroller']")) { return $ui }
    } while ((Get-Date) -lt $limit)
    throw 'Notification shade did not open'
}

function Wait-ReleaseNode([string]$XPath) {
    $deadline = (Get-Date).AddSeconds(30)
    do {
        $ui = Read-ReleaseUi
        $node = $ui.SelectSingleNode($XPath)
        if ($node) { return $node }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $deadline)
    throw "UI element not found: $XPath"
}
function Click-ReleaseNode([string]$XPath) {
    # Probe не ждёт idle из-за часов UI. Даём оконной анимации завершиться до
    # получения координат, чтобы tap не потерялся в переходе между Activity.
    Start-Sleep -Milliseconds 500
    $node = Wait-ReleaseNode $XPath
    if ($node.GetAttribute('enabled') -ne 'true') { throw "Disabled UI element: $XPath" }
    $bounds = [regex]::Match($node.GetAttribute('bounds'), '^\[(\d+),(\d+)\]\[(\d+),(\d+)\]$')
    if (-not $bounds.Success) { throw 'Invalid UI bounds' }
    $x = [int](([int]$bounds.Groups[1].Value + [int]$bounds.Groups[3].Value) / 2)
    $y = [int](([int]$bounds.Groups[2].Value + [int]$bounds.Groups[4].Value) / 2)
    Invoke-ReleaseAdb @('shell', 'input', 'tap', "$x", "$y") | Out-Null
}
function Click-ReleaseId([string]$Id) { Click-ReleaseNode "//node[@resource-id='${package}:id/$Id']" }
# Переход из деталей в Edit завершается только появлением поля формы.
# Если tap потерялся в оконной анимации, повторяем лишь пока видна кнопка деталей;
# не нажимаем по старым координатам и не принимаем ожидание за успешный переход.
function Open-ReleaseEdit {
    $limit = (Get-Date).AddSeconds(30)
    do {
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/edit_task_description']")) { return }
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/btnEditTask']")) { Click-ReleaseId 'btnEditTask' }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limit)
    throw 'Edit form did not open'
}
# Во время анимации Activity tap может потеряться. Перед вводом подтверждаем
# фокус именно поля описания по свежему XML; ограниченные повторные taps безопасны.
function Focus-ReleaseDescription {
    $limit = (Get-Date).AddSeconds(20)
    do {
        Click-ReleaseId 'edit_task_description'
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/edit_task_description' and @focused='true']")) { return }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $limit)
    throw 'Description input did not receive focus'
}
# На API 33 аппаратная клавиатура AVD может оставлять IME скрытой после ввода.
# Back в этом состоянии закроет форму. Сначала сверяем показ IME, затем ещё раз
# проверяем поле формы; системный Back для навигации в других фазах не заменяем.
function Close-ReleaseKeyboard {
    $ime = Invoke-ReleaseAdb @('shell', 'dumpsys', 'input_method')
    if ($ime -match '\bmInputShown=true\b|\bmIsInputViewShown=true\b') {
        Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    }
    Wait-ReleaseNode "//node[@resource-id='${package}:id/edit_task_description']" | Out-Null
}
function Assert-ReleaseText([string]$Text) { Wait-ReleaseNode "//node[@text='$Text']" | Out-Null }
function Launch-Release {
    # После failed-фазы шторка может остаться раскрытой и перекрыть Activity.
    # Сбрасываем только этот UI-слой проектного AVD перед проверкой списка.
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'start', '-W', '-n', "$package/com.vpe_soft.intime.intime.activity.MainActivityV2") | Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/fab_add_task']" | Out-Null
}
function Read-ReleaseAlarm {
    $dump = Invoke-ReleaseAdb @('shell', 'dumpsys', 'alarm')
    # Только действующий RTC_WAKEUP, не историческая запись отменённого будильника.
    # Когда applicationId совпадает с namespace, Android сокращает компонент
    # до «/.receiver.AlarmReceiver»; принимаем также полное имя того же receiver.
    $match = [regex]::Match($dump, '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\s*$')
    if ($match.Success) { return [long]$match.Groups['deadline'].Value }
    return [long]0
}
function Wait-ReleaseAlarm([long]$After = 0) {
    $limit = (Get-Date).AddSeconds(20)
    do {
        $deadline = Read-ReleaseAlarm
        if ($deadline -gt $After) { return $deadline }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limit)
    throw 'Expected active release alarm was not scheduled'
}
function Complete-ReleasePhase([string]$Name) {
    $summary.passedPhases += $Name
    Write-Output "PASSED: $Name"
}

# Задача сохранена UI, exact appop запрещён. Проверяем собственный активный
# AlarmReceiver с тем же deadline и ненулевым окном, затем ждём настоящую доставку
# до 15 минут без Activity/probe/ручного receiver. Фиксированные файлы опроса
# ограничивают расход диска. Требуем ACK, запись receiver и потребление alarm;
# один тихий periodic worker не может подменить успех. При RequireDeepIdle
# каждый опрос и финальный снимок требуют IDLE; квоты/естественный вход не проверяются.
function Assert-ReleaseInexactDelivery([string]$Description, [long]$Deadline, [string]$Since, [bool]$RequireDeepIdle = $false) {
    $ops = Invoke-ReleaseAdb @('shell', 'appops', 'get', $package, 'SCHEDULE_EXACT_ALARM')
    Set-Content (Join-Path $run 'inexact-appops.log') $ops -Encoding UTF8
    if ($ops -notmatch 'SCHEDULE_EXACT_ALARM: deny') { throw 'Exact alarm denial not confirmed' }
    $alarms = Invoke-ReleaseAdb @('shell', 'dumpsys', 'alarm')
    Set-Content (Join-Path $run 'inexact-alarm-before.log') $alarms -Encoding UTF8
    $alarm = [regex]::Match($alarms, '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*?window=(?<window>\S+)')
    if (-not $alarm.Success -or [long]$alarm.Groups['deadline'].Value -ne $Deadline -or $alarm.Groups['window'].Value -notmatch '[1-9]') { throw 'Own inexact alarm with positive window missing' }
    $summary.inexactAlarmWindow = $alarm.Groups['window'].Value
    $summary.exactAlarmDenied = $true
    $limit = (Get-Date).AddMinutes(15)
    $delivered = $false
    $previous = [datetimeoffset]::UtcNow
    do {
        $now = [datetimeoffset]::UtcNow
        $gap = ($now - $previous).TotalSeconds
        if ($gap -lt 0 -or $gap -gt 180) { throw 'Inexact observation interrupted by host polling gap' }
        $previous = $now
        if ($RequireDeepIdle) {
            $state = (Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'cmd', 'deviceidle', 'get', 'deep') 'inexact-idle-poll.log').Trim()
            if ($state -ne 'IDLE') { throw "Deep idle not maintained: $state" }
        }
        $dump = Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', 'notification', '--noredact') 'inexact-notifications-poll.log'
        $record = [regex]::Match($dump, '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
        $when = [regex]::Match($record.Value, '(?m)^\s*when=(\d+)(?:/|\r?$)')
        $delivered = $record.Success -and $record.Value.Contains("android.text=String ($Description)") -and $record.Value -match 'actions=1' -and $when.Success -and [long]$when.Groups[1].Value -ge $Deadline
        if (-not $delivered) { Write-Output 'Waiting for real inexact alarm notification...'; Start-Sleep -Seconds 15 }
    } while (-not $delivered -and (Get-Date) -lt $limit)
    $log = Invoke-ReleaseAdb @('logcat', '-d', '-T', $Since)
    Set-Content (Join-Path $run 'inexact-logcat.log') $log -Encoding UTF8
    if (-not $delivered) { throw 'Inexact alarm notification timed out; delivery not validated' }
    if ($log -notmatch 'scheduleAlarm: exact alarms are not available, using inexact alarm' -or $log -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification') { throw 'Fallback scheduling or actual receiver log missing' }
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Inexact alarm was not consumed' }
    if ($RequireDeepIdle) {
        $state = (Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'get', 'deep')).Trim()
        if ($state -ne 'IDLE') { throw "Deep idle not maintained at delivery: $state" }
        $summary.dozeStateAtDelivery = $state
        Set-Content (Join-Path $run 'doze-state.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')) -Encoding UTF8
    }
    $summary.inexactNotificationWhen = [long]$when.Groups[1].Value
    $summary.inexactDelaySeconds = ($summary.inexactNotificationWhen - $Deadline) / 1000.0
    Complete-ReleasePhase 'release-inexact-fallback-delivery'
}

# Восстанавливаем каждую изменённую настройку даже при ошибке предыдущей команды.
# Вызывается и из finally: отказ проверки доставки не должен оставить forced idle
# или имитацию отключённой зарядки на проектном AVD.
function Restore-ReleaseDoze {
    $errors = @()
    $restoreCommands = @(@('shell', 'cmd', 'deviceidle', 'unforce'), @('shell', 'dumpsys', 'battery', 'reset'), @('shell', 'input', 'keyevent', '224'))
    # AOSP AVD может иметь deep idle выключенным. Возвращаем исходное enabled,
    # не меняя light idle и постоянные whitelist/configuration ОС.
    if ($null -ne $script:dozeDeepWasEnabled -and -not $script:dozeDeepWasEnabled) {
        $restoreCommands += ,@('shell', 'cmd', 'deviceidle', 'disable', 'deep')
    }
    foreach ($arguments in $restoreCommands) {
        try { Invoke-ReleaseAdb $arguments | Out-Null } catch { $errors += $_.Exception.Message }
    }
    if ($errors.Count) { throw ($errors -join '; ') }
    $script:dozeChanged = $false
}

# Tap по строке иногда теряется после перезапуска/перехода. Повторяем его только
# пока свежий XML показывает именно нужную строку списка; появление btnAckTask
# подтверждает экран деталей. ACK не повторяется и выполняется отдельной фазой.
function Open-ReleaseDetails([string]$Description) {
    $limit = (Get-Date).AddSeconds(30)
    $row = "//node[@resource-id='${package}:id/textDescription' and @text='$Description']"
    do {
        $ui = Read-ReleaseUi
        if ($ui.SelectSingleNode("//node[@resource-id='${package}:id/btnAckTask']")) { return }
        if ($ui.SelectSingleNode($row)) { Click-ReleaseNode $row }
        Start-Sleep -Milliseconds 500
    } while ((Get-Date) -lt $limit)
    throw 'Task details did not open'
}

# Будущая минутная задача уже сохранена UI и запланирована приложением, открыт Home.
# На собственном AVD имитируем отключение зарядки, гасим экран, включаем deep idle.
# При ожидании не открываем UI и не запускаем UIAutomator; требуем IDLE при каждом
# опросе и активное уведомление своего пакета с уникальным описанием. Проверяем
# реальный receiver и исчезновение сработавшего alarm, не вызываем Java вручную.
# Покрывается один allow-while-idle alarm: exact либо inexact при InexactOnly.
# Естественный вход, квоты повторной доставки и periodic worker не проверяются.
function Assert-ReleaseDozeDelivery([string]$Description, [long]$Deadline, [string]$SchedulingSince) {
    $battery = Invoke-ReleaseAdb @('shell', 'dumpsys', 'battery')
    Set-Content (Join-Path $run 'doze-battery-before.log') $battery -Encoding UTF8
    if ($battery -match 'UPDATES STOPPED') { throw 'Refusing to overwrite existing battery simulation' }
    $idle = Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')
    Set-Content (Join-Path $run 'doze-state-before.log') $idle -Encoding UTF8
    if ($idle -notmatch 'mForceIdle=false') { throw 'Refusing an already forced idle state' }
    $enabled = (Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'enabled', 'deep')).Trim()
    if ($enabled -notin @('0', '1')) { throw "Unknown deep idle enabled state: $enabled" }
    $script:dozeDeepWasEnabled = $enabled -eq '1'
    $summary.dozeDeepOriginallyEnabled = $script:dozeDeepWasEnabled
    $whitelist = Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'whitelist')
    Set-Content (Join-Path $run 'doze-whitelist.log') $whitelist -Encoding UTF8
    if ($whitelist -match [regex]::Escape($package)) { throw 'Release package is exempt from device idle' }
    $since = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    $script:dozeChanged = $true
    if (-not $script:dozeDeepWasEnabled) { Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'enable', 'deep') | Out-Null }
    Invoke-ReleaseAdb @('shell', 'dumpsys', 'battery', 'unplug') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '223') | Out-Null
    Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'force-idle', 'deep') | Out-Null
    # Fallback должен доставиться при сохранённом deny и положительном окне.
    # Общая проверка требует ACK/receiver и IDLE до пробуждения; cleanup общий.
    if ($InexactOnly) {
        Assert-ReleaseInexactDelivery $Description $Deadline $SchedulingSince $true
        Complete-ReleasePhase 'release-doze-delivery'
        Restore-ReleaseDoze
        return
    }
    $limit = (Get-Date).AddSeconds(150)
    $delivered = $false
    do {
        $state = (Invoke-ReleaseAdb @('shell', 'cmd', 'deviceidle', 'get', 'deep')).Trim()
        if ($state -ne 'IDLE') { throw "Deep idle not maintained: $state" }
        $notifications = Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')
        # Ищем только в активном record своего пакета, не в history/чужих уведомлениях.
        $record = [regex]::Match($notifications, '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*')
        $delivered = $record.Success -and $record.Value.Contains("android.text=String ($Description)")
        if (-not $delivered) { Start-Sleep -Seconds 2 }
    } while (-not $delivered -and (Get-Date) -lt $limit)
    Set-Content (Join-Path $run 'doze-notifications.log') $notifications -Encoding UTF8
    Set-Content (Join-Path $run 'doze-state.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')) -Encoding UTF8
    $deliveryLog = Invoke-ReleaseAdb @('logcat', '-d', '-T', $since)
    Set-Content (Join-Path $run 'doze-logcat.log') $deliveryLog -Encoding UTF8
    if (-not $delivered -or $deliveryLog -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification') { throw 'Release notification not confirmed during deep idle' }
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Doze alarm was not consumed' }
    $summary.dozeStateAtDelivery = $state
    Complete-ReleasePhase 'release-doze-delivery'
    Restore-ReleaseDoze
}

# Начинаем новый минутный отсчёт только через существующую кнопку ACK в UI.
# После возврата Home приложение не подавляет доставку своей видимостью.
function Prepare-ReleaseMinuteAlarm([string]$Description) {
    Launch-Release
    Assert-ReleaseText $Description
    Open-ReleaseDetails $Description
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $Description
    $alarm = Wait-ReleaseAlarm
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    return $alarm
}

# Сохраняем питание, Doze, сборку и uptime Android. Полную очередь JobScheduler
# читаем только перед ожиданием и после фиксации его результата: установленный
# API 36 при dumpsys пересчитывает Ready и может менять TimeController.
# Между началом и концом ожидания эту диагностику вызывать нельзя.
# Три фиксированных набора (before/latest/after) ограничивают расход диска;
# latest перезаписывается раз в пять минут, after снимается и при тайм-ауте.
# Эти dumpsys показывают состояние в момент снимка, но не доказывают причину
# решения планировщика между снимками и могут содержать другие системные пакеты.
function Save-ReleasePeriodicDiagnostics([ValidateSet('before', 'latest', 'after')][string]$Stage) {
    $services = @('power', 'deviceidle', 'battery')
    if ($Stage -ne 'latest') { $services = @('jobscheduler') + $services }
    foreach ($service in $services) {
        Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', $service) "periodic-diagnostic-$Stage-$service.log" | Out-Null
    }
    Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'cat', '/proc/uptime') "periodic-diagnostic-$Stage-uptime.log" | Out-Null
    Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'getprop', 'ro.build.fingerprint') 'periodic-build-fingerprint.log' | Out-Null
    Set-Content (Join-Path $run "periodic-diagnostic-$Stage-time.txt") ([datetimeoffset]::Now.ToString('o')) -Encoding UTF8
}

# Опрос выполняется каждые 30 секунд. Разрыв свыше трёх минут или перевод
# часов назад означает, что непрерывность наблюдения потеряна. Причину (сон,
# приостановку VM или изменение часов) эта проверка не устанавливает.
# Проверяем и последнюю паузу после выхода из цикла: из-за скачка часов
# условие while может завершить цикл раньше следующего опроса.
function Assert-ReleasePeriodicPollContinuity([datetimeoffset]$Previous, [datetimeoffset]$Current) {
    $gap = ($Current - $Previous).TotalSeconds
    if ($gap -lt 0 -or $gap -gt 180) {
        $summary.periodicObservationInterrupted = $true
        $summary.periodicHostPollGapSeconds = $gap
        $summary.naturalDispatchValidated = $false
        throw 'Periodic observation interrupted by host polling gap; continuous device wait not validated'
    }
}

# Чистая release-установка уже прошла CRUD/JSON/reboot и доставила настоящий
# минутный alarm с ACK. После Home убиваем только фоновый процесс (не force-stop),
# подтверждаем stopped=false и оставшуюся SystemJobService. Ждём новое активное
# уведомление с тем же уникальным описанием и when >= первой отправки + 15 минут.
# Проверяем отсутствие ACK, приоритет LOW, null sound/vibration и defaults=0
# в Notification, SILENT на API 35/36 и подавление оповещения группы. mSound в dumpsys
# может сохранять звук канала даже при SILENT, поэтому его null не требуем.
# а также SUCCESS настоящего WorkManager worker в logcat после смерти процесса.
# Штатный режим не вызывает job вручную. Если host отдельно выполнил контролируемый
# dispatch, он обязан сохранить маркер и вывод команды; итог будет PASSED_CONTROLLED,
# а не подтверждением естественной доставки. Не читаем private Room/preferences,
# поэтому сопоставление work UUID с private Room и wasNotified не утверждаем.
# PeriodicRepeats=2 проверяет следующий период без вмешательства между повторами.
# Doze/OEM и точное время запуска этой проверкой не покрываются.
# Проверяем именно объект Notification, а не настройки канала. API 31/33 печатают
# defaults/flags в hex, API 35/36 — ноль и символические флаги. На API 31/33 нет
# текстового SILENT: требуем ONLY_ALERT_ONCE (0x8), AUTO_CANCEL (0x10),
# groupAlertBehavior=1 и null sound/vibrate с нулевыми defaults. Звук динамика
# не измеряется; значение mSound канала не подменяет эти проверки.
function Assert-ReleaseQuietNotification([string]$Record) {
    if ($Record -match 'actions=[1-9]\d*|actions=\{' -or $Record -notmatch '(?m)^\s*pri=-1\s*$' -or $Record -notmatch 'vibrate=null sound=null defaults=(?:0x0|0)(?=\s)' -or $Record -notmatch '(?m)^\s*groupAlertBehavior=1\s*$') { throw 'Repeat is not a quiet notification without ACK' }
    if ($TestApi -le 33) {
        $flags = [regex]::Match($Record, '\bflags=0x([0-9a-fA-F]+)\b')
        if (-not $flags.Success -or ([Convert]::ToInt64($flags.Groups[1].Value, 16) -band 0x18) -ne 0x18) { throw "Quiet notification flags missing on API $TestApi" }
    } elseif ($Record -notmatch 'flags=[^\r\n]*SILENT') { throw 'Quiet notification SILENT flag missing' }
}

function Assert-ReleasePeriodicReminder([string]$Description) {
    $dump = Get-Content (Join-Path $run 'notifications-delivered.log') -Raw -Encoding UTF8
    $pattern = '(?ms)^\s*NotificationRecord\([^\r\n]*pkg=com\.vpe_soft\.intime\.intime [^\r\n]*\r?\n(?:(?!^\s*NotificationRecord\().)*'
    $initial = [regex]::Match($dump, $pattern)
    # Старые платформы печатают только when, новые добавляют /postingTime.
    $first = [regex]::Match($initial.Value, '(?m)^\s*when=(\d+)(?:/|\r?$)')
    if (-not $initial.Success -or -not $first.Success -or -not $initial.Value.Contains("android.text=String ($Description)") -or $initial.Value -notmatch 'actions=1') { throw 'Initial alarm record with ACK missing' }
    $firstPost = [long]$first.Groups[1].Value
    $summary.periodicFirstAlarmWhen = $firstPost
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    $since = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    $summary.periodicBatchingChanged = $false
    Set-Content (Join-Path $run 'periodic-jobs-before.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'jobscheduler')) -Encoding UTF8
    if ((Get-Content (Join-Path $run 'periodic-jobs-before.log') -Raw) -notmatch 'com\.vpe_soft\.intime\.intime/androidx\.work\.impl\.background\.systemjob\.SystemJobService') { throw 'Release WorkManager platform job missing' }
    Invoke-ReleaseAdb @('shell', 'am', 'kill', $package) | Out-Null
    Start-Sleep -Seconds 2
    if ((Invoke-ReleaseAdb @('shell', "pidof $package || true")).Trim()) { throw 'Release process survived am kill' }
    $state = Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)
    Set-Content (Join-Path $run 'periodic-package-after-kill.log') $state -Encoding UTF8
    if ($state -notmatch 'User 0:.*stopped=false') { throw 'Release package stopped after kill' }
    $summary.periodicProcessAbsentAfterKill = $true
    $summary.periodicLiveJobDumps = $false
    Save-ReleasePeriodicDiagnostics 'before'
    $limit = (Get-Date).AddMinutes($PeriodicWaitMinutes)
    $timer = [Diagnostics.Stopwatch]::StartNew()
    $nextDiagnosticSeconds = 300
    $lastPollAt = [datetimeoffset]::UtcNow
    $observationError = $null
    $delivered = $false
    $posts = @()
    $previousPost = $firstPost
    Write-Output "Waiting for natural release worker (up to $PeriodicWaitMinutes minutes, unchanged batching)..."
    do {
        $pollAt = [datetimeoffset]::UtcNow
        try { Assert-ReleasePeriodicPollContinuity $lastPollAt $pollAt }
        catch { $observationError = $_.Exception.Message; break }
        $lastPollAt = $pollAt
        # Перезаписываем снимок опроса, чтобы длительное ожидание не раздувало диск.
        # Не вызываем UI/probe/Activity/instrumentation/ручной JobScheduler run.
        $notifications = Invoke-ReleaseTool $adb @('-s', $serial, 'shell', 'dumpsys', 'notification', '--noredact') 'periodic-notifications-poll.log'
        $record = [regex]::Match($notifications, $pattern)
        $when = [regex]::Match($record.Value, '(?m)^\s*when=(\d+)(?:/|\r?$)')
        # Один и тот же активный record не считается новым повтором. Каждый новый
        # when должен отстоять минимум на штатные 15 минут от предыдущей отправки.
        if ($record.Success -and $record.Value.Contains("android.text=String ($Description)") -and $when.Success -and [long]$when.Groups[1].Value -ge $previousPost + 900000) {
            Assert-ReleaseQuietNotification $record.Value
            $post = [long]$when.Groups[1].Value
            $posts += @{ when = $post; gapSeconds = ($post - $previousPost) / 1000.0 }
            $previousPost = $post
            # Сохраняем оба доказательства; остальные опросы перезаписываем.
            Set-Content (Join-Path $run ("periodic-notifications-repeat-{0}.log" -f $posts.Count)) $notifications -Encoding UTF8
            $summary.periodicObservedRepeats = $posts
            Write-Output ("Natural quiet repeat {0}/{1}, gap {2:N1} seconds" -f $posts.Count, $PeriodicRepeats, $posts[-1].gapSeconds)
            if ($posts.Count -eq $PeriodicRepeats) { $delivered = $true; break }
        }
        if ($timer.Elapsed.TotalSeconds -ge $nextDiagnosticSeconds) {
            Save-ReleasePeriodicDiagnostics 'latest'
            $nextDiagnosticSeconds = $timer.Elapsed.TotalSeconds + 300
        }
        Write-Output ("Worker wait: {0:N1} minutes" -f $timer.Elapsed.TotalMinutes)
        Start-Sleep -Seconds 30
    } while ((Get-Date) -lt $limit)
    if (-not $observationError) {
        try { Assert-ReleasePeriodicPollContinuity $lastPollAt ([datetimeoffset]::UtcNow) }
        catch { $observationError = $_.Exception.Message }
    }
    Save-ReleasePeriodicDiagnostics 'after'
    Set-Content (Join-Path $run 'periodic-jobs-after.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'jobscheduler')) -Encoding UTF8
    $log = Invoke-ReleaseAdb @('logcat', '-d', '-T', $since)
    Set-Content (Join-Path $run 'periodic-logcat.log') $log -Encoding UTF8
    if ($observationError) { throw $observationError }
    if (-not $delivered) { throw 'Natural release worker notification timed out; inspect platform scheduling before blaming application' }
    Assert-ReleaseQuietNotification $record.Value
    # Logcat читаем только после всего окна. Для двух повторов нужны две записи
    # SUCCESS настоящего worker; одно уведомление/один SUCCESS нельзя засчитать дважды.
    $successes = [regex]::Matches($log, '(?m)^.*Worker result SUCCESS for Work[^\r\n]*TaskNotificationWorker[^\r\n]*$')
    if ($successes.Count -lt $PeriodicRepeats) { throw 'Actual WorkManager SUCCESS count is below requested repeats after process termination' }
    $summary.periodicWorkerSuccessCount = $successes.Count
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Unexpected future alarm after overdue repeat' }
    $summary.periodicQuietNotificationWhen = [long]$when.Groups[1].Value
    $summary.periodicGapSeconds = ($summary.periodicQuietNotificationWhen - $firstPost) / 1000.0
    Complete-ReleasePhase 'release-periodic-quiet-after-process-exit'
    if ($PeriodicRepeats -eq 2) { Complete-ReleasePhase 'release-periodic-next-natural-period' }
    $summary.naturalDispatchValidated = $true
}

# Метаданные отдельного host-dispatch не запускают команды. Они только уточняют
# область фактической проверки: PASS после явного JobScheduler run нельзя выдавать
# за естественную доставку. Маркер без подтверждённого Running job отвергается.
# Применяется после сбора диагностики/очистки, не затрагивает APK или настройки ОС.
function Set-ReleaseDispatchAssessment([hashtable]$Result, [string]$RunDirectory) {
    $marker = Join-Path $RunDirectory 'periodic-dispatch-control.json'
    if (-not (Test-Path -LiteralPath $marker)) { return }
    $control = Get-Content -LiteralPath $marker -Raw -Encoding UTF8 | ConvertFrom-Json
    $output = Get-Content (Join-Path $RunDirectory 'periodic-controlled-run.log') -Raw -Encoding UTF8
    if ($control.ownJobCount -ne 1 -or "$($control.jobId)" -notmatch '^\d+$' -or $output -notmatch '(?m)^Running job\s*$') { throw 'Controlled dispatch evidence is incomplete' }
    $Result.periodicPlatformJobDispatched = $true
    $Result.naturalDispatchValidated = $false
    $Result.periodicDispatchCommand = 'cmd jobscheduler run -s -n androidx.work.systemjobscheduler com.vpe_soft.intime.intime ' + $control.jobId
    $Result.naturalObservationSeconds = (([datetimeoffset]$control.observedAt).ToUnixTimeMilliseconds() - [long]$Result.periodicFirstAlarmWhen) / 1000.0
    if ($Result.status -eq 'PASSED') { $Result.status = 'PASSED_CONTROLLED_PLATFORM_DISPATCH' }
}

# Повторно опрашиваем шторку до пяти секунд после срока alarm; между снимками
# UI есть паузы, поэтому кратковременное появление между ними не исключается.
# Дополнительно требуем исчезновения сработавшего alarm и запись receiver в logcat,
# чтобы отсутствие уведомления не стало ложным успехом при несработавшем будильнике.
# wasNotified в приватной Room этим black-box наблюдением не проверяется.
function Assert-ReleaseBlockedNotification([long]$Alarm, [string]$Description, [string]$Name) {
    $since = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $limit = (Get-Date).AddSeconds(150)
    $finished = $false
    do {
        $ui = Read-ReleaseShade
        if ($ui.SelectSingleNode("//node[@text='$Description']")) { throw "Blocked notification visible: $Name" }
        $now = [long](Invoke-ReleaseAdb @('shell', 'date', '+%s')).Trim() * 1000
        if ($now -ge $Alarm + 5000 -and (Read-ReleaseAlarm) -eq 0) { $finished = $true; break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $limit)
    if (-not $finished) { throw "Blocked alarm did not finish: $Name" }
    $phaseLog = Invoke-ReleaseAdb @('logcat', '-d', '-T', $since)
    Set-Content (Join-Path $run "$Name-logcat.log") $phaseLog -Encoding UTF8
    if ($phaseLog -notmatch 'AlarmReceiver.*handleAlarm: trying to post notification') { throw "Receiver delivery not confirmed: $Name" }
    Set-Content (Join-Path $run "$Name-notifications.log") (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Launch-Release
    Assert-ReleaseText $Description
}

# Восстановление доставки проверяем новым UI ACK существующей задачи: её описание
# остаётся в списке, но срок начинается заново. ID отдельно не сверяется. Это не доказательство сохранения
# wasNotified у уже просроченной задачи после снятия блокировки.
function Assert-ReleaseRecoveredNotification([string]$Description, [string]$Name) {
    $alarm = Prepare-ReleaseMinuteAlarm $Description
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $limit = (Get-Date).AddSeconds(150)
    $visible = $false
    do {
        $ui = Read-ReleaseShade
        $visible = $null -ne $ui.SelectSingleNode("//node[@text='$Description']")
        if (-not $visible) { Start-Sleep -Seconds 2 }
    } while (-not $visible -and (Get-Date) -lt $limit)
    if (-not $visible) { throw "Notification did not recover: $Name" }
    Set-Content (Join-Path $run "$Name-notifications.log") (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Click-ReleaseNode "//node[@text='Acknowledge' or @text='ACKNOWLEDGE']"
    $next = Wait-ReleaseAlarm $alarm
    $summary[$Name] = @{ deliveredAlarm = $alarm; acknowledgedAlarm = $next }
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Launch-Release
    Assert-ReleaseText $Description
}

# API 31: меняем общий доступ через системный экран собственного приложения.
# В отличие от appops POST_NOTIFICATION, Settings меняет notification preferences.
# Требуем состояние главного переключателя и importance именно собственного пакета.
# Тест не проверяет permission dialog API 33+ и не трогает чужие приложения.
function Set-ReleaseAppNotificationsEnabled([bool]$Enabled) {
    Invoke-ReleaseAdb @('shell', 'am', 'start', '-W', '-a', 'android.settings.APP_NOTIFICATION_SETTINGS', '--es', 'android.provider.extra.APP_PACKAGE', $package) | Out-Null
    $xpath = "//node[(@resource-id='android:id/switch_widget' or @resource-id='com.android.settings:id/switch_widget') and @checkable='true']"
    $switch = Wait-ReleaseNode $xpath
    $expected = if ($Enabled) { 'true' } else { 'false' }
    if ($switch.GetAttribute('checked') -ne $expected) { Click-ReleaseNode $xpath }
    Wait-ReleaseNode ($xpath.TrimEnd(']') + " and @checked='$expected']") | Out-Null
    $dump = Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')
    $app = [regex]::Match($dump, '(?m)^\s*AppSettings: com\.vpe_soft\.intime\.intime \(\d+\)(?<details>[^\r\n]*)$')
    if (-not $app.Success) { throw 'Own app notification preferences not found' }
    # Android 12 опускает importance при возвращении к значению по умолчанию.
    # Наличие строки своего пакета обязательно; NONE/0 означает общий запрет.
    $blocked = $app.Groups['details'].Value -match '\bimportance=(NONE|0)(?:\s|$)'
    if ($blocked -eq $Enabled) { throw 'Own app notification importance does not match switch' }
    Set-Content (Join-Path $run ("app-notifications-$expected-settings.log")) $dump -Encoding UTF8
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
}

# Меняем только канал собственного production-пакета через системный экран.
# Должен измениться именно переключатель Show notifications; системные каналы
# и общий доступ приложения к уведомлениям этим действием не меняются.
function Set-ReleaseChannelEnabled([bool]$Enabled) {
    Invoke-ReleaseAdb @('shell', 'am', 'start', '-W', '-a', 'android.settings.CHANNEL_NOTIFICATION_SETTINGS', '--es', 'android.provider.extra.APP_PACKAGE', $package, '--es', 'android.provider.extra.CHANNEL_ID', 'DefaultChannelId') | Out-Null
    $xpath = "//node[@resource-id='android:id/switch_widget' and @checkable='true']"
    $switch = Wait-ReleaseNode $xpath
    $expected = if ($Enabled) { 'true' } else { 'false' }
    if ($switch.GetAttribute('checked') -ne $expected) { Click-ReleaseNode $xpath }
    Wait-ReleaseNode ($xpath.TrimEnd(']') + " and @checked='$expected']") | Out-Null
    $dump = Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')
    $app = [regex]::Match($dump, '(?ms)^\s*AppSettings: com\.vpe_soft\.intime\.intime \(\d+\)[^\r\n]*\r?\n(?<body>.*?)(?=^\s*AppSettings:|^Restored without uid:|\z)')
    $channel = [regex]::Match($app.Groups['body'].Value, "NotificationChannel\{mId='DefaultChannelId'[^\r\n]*?mImportance=(\d+),")
    if (-not $app.Success -or -not $channel.Success) { throw 'Own channel state not found in dumpsys' }
    $importance = [int]$channel.Groups[1].Value
    if (($Enabled -and $importance -eq 0) -or (-not $Enabled -and $importance -ne 0)) { throw 'Own channel importance does not match switch' }
    Set-Content (Join-Path $run ("channel-$expected-settings.log")) $dump -Encoding UTF8
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
}

try {
    $lock = [IO.File]::Open((Join-Path $work 'run.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
    $state = Get-Content (Join-Path $work 'emulator.json') -Raw | ConvertFrom-Json
    $process = Get-Process -Id $state.processId -ErrorAction Stop
    if ($state.serial -ne $serial -or $process.StartTime.ToUniversalTime().ToString('o') -ne $state.started) { throw 'Emulator ownership mismatch' }
    $name = Invoke-ReleaseAdb @('emu', 'avd', 'name')
    if ($name.Split("`n")[0].Trim() -ne "Intime_Test_API$TestApi") { throw 'Refusing another AVD' }
    if ((Invoke-ReleaseAdb @('shell', 'getprop', 'ro.build.version.sdk')).Trim() -ne "$TestApi") { throw "Expected API $TestApi" }
    if ((Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')) -match 'Application Not Responding:') { throw 'System ANR dialog is present' }
    $ApkPath = (Resolve-Path -LiteralPath $ApkPath).Path
    $tools = Join-Path $sdk 'build-tools\35.0.0'
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
    $badging = (& (Join-Path $tools 'aapt.exe') dump badging $ApkPath) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $badging -notmatch "package: name='$package' versionCode='25'" -or $badging -match 'application-debuggable') { throw 'Expected non-debuggable production candidate code 25' }
    Set-Content (Join-Path $run 'apk-badging.log') $badging -Encoding UTF8
    $signing = (& (Join-Path $tools 'apksigner.bat') verify --print-certs $ApkPath 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $signing -notmatch '97b60110a8e894791b4770826a8d214dc5e3a551cd24bd41d646c29f64a1dd26') { throw 'APK upload signature mismatch' }
    Set-Content (Join-Path $run 'apk-signing.log') $signing -Encoding UTF8
    $summary.apkSha256 = (Get-FileHash $ApkPath -Algorithm SHA256).Hash
    $summary.apk = $ApkPath
    # Mapping должен сопровождать APK из того же сохранённого build-прогона.
    # Проверяем, что R8 действительно переименовал release-репозиторий; одной
    # отметки non-debuggable недостаточно для утверждения о проверке R8.
    $mappingPath = Join-Path (Split-Path $ApkPath -Parent) 'mapping.txt'
    $mapping = Get-Content $mappingPath -Raw
    $renamed = [regex]::Match($mapping, '(?m)^com\.vpe_soft\.intime\.intime\.database\.repositories\.TaskRepository -> ([^:]+):')
    if (-not $renamed.Success -or $renamed.Groups[1].Value -eq 'com.vpe_soft.intime.intime.database.repositories.TaskRepository') { throw 'Expected R8 repository renaming in accompanying mapping.txt' }
    $summary.mappingSha256 = (Get-FileHash $mappingPath -Algorithm SHA256).Hash
    # Компилируем отдельный shell-probe SDK-инструментами. Legacy UIAutomator
    # используется только для свежего XML без idle timeout: зависимости и хуки
    # в release APK не добавляются. Сам CRUD проверяется ниже по реальному UI.
    $classes = Join-Path $run 'probe-classes'
    $dex = Join-Path $run 'probe-dex'
    New-Item -ItemType Directory -Path $classes, $dex -Force | Out-Null
    $platform = Join-Path $sdk 'platforms\android-36'
    $junit = Get-ChildItem (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.gradle\caches\modules-2\files-2.1\junit\junit\4.13.2') -Recurse -Filter 'junit-4.13.2.jar' | Select-Object -First 1
    if (-not $junit) { throw 'JUnit compile dependency missing; run test.ps1 bootstrap first' }
    Invoke-ReleaseTool (Join-Path $env:JAVA_HOME 'bin\javac.exe') @('-encoding', 'UTF-8', '--release', '8', '-classpath', "$(Join-Path $platform 'android.jar');$(Join-Path $platform 'uiautomator.jar');$($junit.FullName)", '-d', $classes, (Join-Path $PSScriptRoot 'android\ReleaseHierarchyProbe.java')) 'probe-compile.log' | Out-Null
    Invoke-ReleaseTool (Join-Path $tools 'd8.bat') @('--min-api', '24', '--output', $dex, (Join-Path $classes 'intime\smoke\ReleaseHierarchyProbe.class')) 'probe-dex.log' | Out-Null
    $probeJar = Join-Path $run 'intime-release-probe.jar'
    Invoke-ReleaseTool (Join-Path $env:JAVA_HOME 'bin\jar.exe') @('cf', $probeJar, '-C', $dex, 'classes.dex') 'probe-package.log' | Out-Null
    Invoke-ReleaseAdb @('push', $probeJar, '/data/local/tmp/intime-release-probe.jar') | Out-Null
    $probePushed = $true
    # pm path возвращает exit 1 для отсутствующего пакета. Для проверки отсутствия
    # используем успешный list packages и точное совпадение, исключая соседний .dev.
    if ((Invoke-ReleaseAdb @('shell', 'pm', 'list', 'packages', $package)) -match '(?m)^package:com\.vpe_soft\.intime\.intime\r?$') { throw 'Production package already exists; refusing to replace or clear it' }
    Invoke-ReleaseAdb @('logcat', '-c') | Out-Null # Начало журнала только этого smoke на тестовом AVD.
    Invoke-ReleaseAdb @('install', $ApkPath) | Out-Null
    $installedHere = $true
    # На API 31 runtime POST_NOTIFICATIONS отсутствует; начальная установка
    # получает обычный доступ к уведомлениям без команды pm grant.
    if ($TestApi -ge 33) { Invoke-ReleaseAdb @('shell', 'pm', 'grant', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null }
    Launch-Release

    # Проверка создания: чистая release-установка, разрешение уведомлений выдано.
    # Через форму вводим одну задачу с периодом час; список должен показать описание,
    # а AlarmManager — действующий будильник. Этим проверяется реальный Room/репозиторий
    # после R8, без in-memory базы и без вызова внутренних методов приложения.
    $description = 'R8-Smoke-' + (Get-Date -Format 'HHmmss')
    Click-ReleaseId 'fab_add_task'
    Focus-ReleaseDescription
    Invoke-ReleaseAdb @('shell', 'input', 'text', $description) | Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/edit_task_description' and @text='$description']" | Out-Null
    Close-ReleaseKeyboard
    Click-ReleaseId 'spinner_interval'
    Click-ReleaseNode "//node[@text='Час']"
    Click-ReleaseId 'btn_save_task'
    Wait-ReleaseNode "//node[@resource-id='${package}:id/textDescription' and @text='$description']" | Out-Null
    $beforeAck = Wait-ReleaseAlarm
    Complete-ReleasePhase 'create-and-alarm'

    # Проверка редактирования: открываем созданную строку и форму Edit, дописываем
    # суффикс. После сохранения старое описание заменяется новым; повторный запуск
    # после force-stop должен прочитать это же значение из файловой Room.
    Open-ReleaseDetails $description
    Open-ReleaseEdit
    Focus-ReleaseDescription
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END') | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'text', '-Edited') | Out-Null
    Wait-ReleaseNode "//node[@resource-id='${package}:id/edit_task_description' and @text='$description-Edited']" | Out-Null
    Close-ReleaseKeyboard
    Click-ReleaseId 'btn_save_task'
    $edited = "$description-Edited"
    Assert-ReleaseText $edited
    Click-ReleaseId 'btn_back_to_main'
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    Complete-ReleasePhase 'edit-and-process-restart'

    # Проверка ACK: подтверждаем сохранённую задачу кнопкой деталей. Она остаётся
    # в списке, а срок действующего будильника должен стать позднее исходного.
    # Это проверяет прохождение ACK через release-репозиторий и пересчёт расписания;
    # доставку уведомления и действие ACK из шторки эта фаза не проверяет.
    # Снимок срока берём после Edit/restart, иначе перенос при редактировании
    # мог бы дать ложный успех даже при неработающем ACK.
    $beforeAck = Wait-ReleaseAlarm
    Open-ReleaseDetails $edited
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $edited
    $afterAck = Wait-ReleaseAlarm $beforeAck
    $summary.alarmBeforeAck = $beforeAck
    $summary.alarmAfterAck = $afterAck
    Complete-ReleasePhase 'ack-and-reschedule'

    # Проверка настроек и Back: экран с DataBinding должен показать версию кандидата;
    # системный Back возвращает список с задачей. Внешние настройки ОС не изменяем.
    Click-ReleaseId 'btn_open_settings'
    Wait-ReleaseNode "//node[contains(@text, '2.0.0-rc1')]" | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    Assert-ReleaseText $edited
    Complete-ReleasePhase 'settings-and-system-back'

    # Проверка экспорта: после ACK существует одна отредактированная задача.
    # Сохраняем JSON через настоящий системный SAF picker в Downloads, читаем
    # только созданный нами файл и проверяем версию, порядок восьми колонок,
    # описание и совпадение next_alarm с действующим AlarmManager. Это проверяет
    # release-сериализацию и запись ContentResolver; чужие файлы не читаются.
    $backupName = 'intime-r8-' + [guid]::NewGuid().ToString('N') + '.json'
    $backupDevicePath = '/sdcard/Download/' + $backupName
    Click-ReleaseId 'btn_open_settings'
    Click-ReleaseId 'export_to_json_btn'
    Click-ReleaseNode "//node[@class='android.widget.EditText']"
    # API 31 интерпретирует shell keycombination Ctrl+A как ввод буквы a.
    # Вместо него читаем текущее имя, переводим курсор в конец и удаляем символы
    # обычными DEL. Перед Save требуем точное новое имя по свежему XML.
    $nameField = Wait-ReleaseNode "//node[@class='android.widget.EditText' and @focused='true']"
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END') | Out-Null
    $nameLength = $nameField.GetAttribute('text').Length
    if ($nameLength -gt 0) { Invoke-ReleaseAdb (@('shell', 'input', 'keyevent') + (@('KEYCODE_DEL') * $nameLength)) | Out-Null }
    Invoke-ReleaseAdb @('shell', 'input', 'text', $backupName) | Out-Null
    Wait-ReleaseNode "//node[@class='android.widget.EditText' and @text='$backupName']" | Out-Null
    Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/export_to_json_btn']" | Out-Null
    $backupLocalPath = Join-Path $run $backupName
    Invoke-ReleaseAdb @('pull', $backupDevicePath, $backupLocalPath) | Out-Null
    $backup = Get-Content $backupLocalPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($backup.meta.version -ne 1 -or ($backup.tables.tasks.columns -join ',') -ne 'id,description,interval,amount,next_alarm,next_caution,last_ack,quant') { throw 'Invalid exported JSON contract' }
    $rows = @($backup.tables.tasks.rows)
    if ($rows.Count -ne 1 -or $rows[0].Count -ne 8 -or $rows[0][1] -ne $edited -or [long]$rows[0][4] -ne $afterAck) { throw 'Exported task does not match UI/alarm' }
    $summary.exportSha256 = (Get-FileHash $backupLocalPath -Algorithm SHA256).Hash
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    Complete-ReleasePhase 'json-export'

    # Проверка удаления: нажимаем Delete и подтверждаем диалог. Должно появиться
    # пустое состояние, которое сохраняется после нового запуска процесса;
    # будильник единственной удалённой задачи должен быть отменён.
    Open-ReleaseDetails $edited
    Click-ReleaseId 'btnDeleteTask'
    Click-ReleaseNode "//node[@resource-id='android:id/button1']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/emptyStateAddTaskBtn']" | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Wait-ReleaseNode "//node[@resource-id='${package}:id/emptyStateAddTaskBtn']" | Out-Null
    if ((Read-ReleaseAlarm) -ne 0) { throw 'Deleted task still has an active alarm' }
    Complete-ReleasePhase 'delete-and-empty-restart'

    # Проверка импорта: список пуст после удаления и перезапуска процесса.
    # Выбираем собственный экспорт через SAF, затем проверяем восстановленное
    # описание и точно прежний срок alarm, включая повторный запуск приложения.
    # Таким образом данные проходят настоящий release parser/Room/ContentResolver;
    # ошибочные файлы и откат транзакции эта фаза пока не моделирует.
    Click-ReleaseId 'btn_open_settings'
    Click-ReleaseId 'import_from_json_btn'
    Click-ReleaseNode "//node[@text='$backupName']"
    Wait-ReleaseNode "//node[@resource-id='${package}:id/import_from_json_btn']" | Out-Null
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '4') | Out-Null
    Assert-ReleaseText $edited
    $restoredAlarm = Wait-ReleaseAlarm
    if ($restoredAlarm -ne $afterAck) { throw 'Imported alarm deadline changed' }
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    if ((Wait-ReleaseAlarm) -ne $afterAck) { throw 'Imported alarm not restored after process restart' }
    $summary.importedAlarm = $restoredAlarm
    Complete-ReleasePhase 'json-import-and-restart'

    # Проверка BootReceiver: импортированная часовая задача сохранена, приложение
    # запускалось и не находится в stopped. Уходим Home, записываем точный срок
    # alarm и boot_count, выполняем настоящий reboot только принадлежащего нам AVD.
    # После нового boot_count ждём тот же alarm ДО запуска Activity: восстановить
    # его должен release BootReceiver, а не побочный эффект открытия приложения.
    # Просрочку во время выключения и boot-уведомление эта фаза не моделирует.
    for ($bootIteration = 1; $bootIteration -le $RebootRepeats; $bootIteration++) {
    # Каждый повтор воспроизводит force-stop/запуск, а не только reboot уже стабильного
    # пакета. Проверяем сохранённое описание и срок до Home; после reboot Activity
    # не открывается до проверки alarm. Не читаем private DB и не вызываем receiver вручную.
    if ($bootIteration -gt 1) {
        Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
        Launch-Release
        Assert-ReleaseText $edited
        if ((Wait-ReleaseAlarm) -ne $afterAck) { throw 'Alarm changed before repeated reboot' }
    }
    $bootPrefix = "reboot-$bootIteration"
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    $bootBefore = [int](Invoke-ReleaseAdb @('shell', 'settings', 'get', 'global', 'boot_count')).Trim()
    $expectedBootAlarm = Wait-ReleaseAlarm
    # API 31: после force-stop/запуска оставляем ОС время зафиксировать состояние
    # пакета перед reboot. Отсутствие BOOT_COMPLETED однажды наблюдалось, но связь
    # с отложенной записью PackageManager не доказана; сохраняем состояние для диагностики.
    if ($TestApi -eq 31 -and $RebootSettleSeconds -gt 0) { Start-Sleep -Seconds $RebootSettleSeconds }
    # API 36: после force-stop/повторного запуска однажды наблюдалось
    # stopped=false до reboot и stopped=true после него, без вызова BootReceiver.
    # Дожидаемся штатных очередей PackageManager перед reboot, чтобы отделить
    # подготовку host от проверки восстановления. Команды подтверждены справкой
    # этого AVD. Они не снимают stopped принудительно и не вызывают receiver.
    # Ожидание очередей не доказывает причину прежнего сбоя или запись всех файлов.
    if ($TestApi -eq 36) {
        Set-Content (Join-Path $run "$bootPrefix-package-handler-wait.log") (Invoke-ReleaseAdb @('shell', 'cmd', 'package', 'wait-for-handler', '--timeout', '30000')) -Encoding UTF8
        Set-Content (Join-Path $run "$bootPrefix-package-background-handler-wait.log") (Invoke-ReleaseAdb @('shell', 'cmd', 'package', 'wait-for-background-handler', '--timeout', '30000')) -Encoding UTF8
        $summary.packageManagerHandlersWaited = $true
    }
    $beforeBootPackage = Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)
    Set-Content (Join-Path $run 'package-before-reboot.log') $beforeBootPackage -Encoding UTF8
    Set-Content (Join-Path $run "$bootPrefix-package-before.log") $beforeBootPackage -Encoding UTF8
    if ($beforeBootPackage -notmatch 'User 0:.*stopped=false') { throw 'Release package stopped before reboot' }
    Set-Content (Join-Path $run 'logcat-before-reboot.log') (Invoke-ReleaseAdb @('logcat', '-d')) -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $run 'logcat-before-reboot.log') -Destination (Join-Path $run "$bootPrefix-logcat-before.log")
    if ((Get-Content (Join-Path $run "$bootPrefix-logcat-before.log") -Raw) -match 'ANR in|FATAL EXCEPTION') { throw 'ANR or fatal exception before repeated reboot' }
    Invoke-ReleaseAdb @('reboot') | Out-Null
    Write-Output 'Waiting for owned AVD reboot...'
    $bootLimit = (Get-Date).AddSeconds(300)
    $bootReady = $false
    do {
        try {
            $bootFlag = (Invoke-ReleaseAdb @('shell', 'getprop', 'sys.boot_completed')).Trim()
            $bootAfter = [int](Invoke-ReleaseAdb @('shell', 'settings', 'get', 'global', 'boot_count')).Trim()
            $bootReady = $bootFlag -eq '1' -and $bootAfter -gt $bootBefore
        } catch { $bootReady = $false }
        if (-not $bootReady) { Start-Sleep -Seconds 2 }
    } while (-not $bootReady -and (Get-Date) -lt $bootLimit)
    if (-not $bootReady) { throw 'Owned AVD reboot timed out' }
    $bootAlarmLimit = (Get-Date).AddSeconds(180)
    do {
        $bootAlarm = Read-ReleaseAlarm
        if ($bootAlarm -eq $expectedBootAlarm) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $bootAlarmLimit)
    Set-Content (Join-Path $run 'package-after-reboot.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)) -Encoding UTF8
    Copy-Item -LiteralPath (Join-Path $run 'package-after-reboot.log') -Destination (Join-Path $run "$bootPrefix-package-after.log")
    # Сохраняем broadcast history и logcat также при успехе: сравнение только
    # неуспешных запусков не показывает, доставлялся ли BOOT_COMPLETED в норме.
    Set-Content (Join-Path $run "$bootPrefix-broadcasts-after.log") (Invoke-ReleaseAdb @('shell', 'dumpsys', 'activity', 'broadcasts')) -Encoding UTF8
    $bootLog = Invoke-ReleaseAdb @('logcat', '-d')
    Set-Content (Join-Path $run "$bootPrefix-logcat-after.log") $bootLog -Encoding UTF8
    $summary.rebootResults += @{ iteration = $bootIteration; bootBefore = $bootBefore; bootAfter = $bootAfter; expectedAlarm = $expectedBootAlarm; actualAlarm = $bootAlarm; receiverLogged = [bool]($bootLog -match 'BootReceiver.*onReceive'); restored = ($bootAlarm -eq $expectedBootAlarm) }
    if ($bootLog -match 'ANR in|FATAL EXCEPTION') { throw 'ANR or fatal exception during repeated reboot' }
    if ($bootAlarm -ne $expectedBootAlarm) {
        Set-Content (Join-Path $run 'broadcasts-after-reboot.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'activity', 'broadcasts')) -Encoding UTF8
        throw 'Release BootReceiver did not restore the same alarm'
    }
    if ((Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')) -match 'Application Not Responding:') { throw 'ANR dialog after reboot' }
    $summary.bootCountBefore = $bootBefore
    $summary.bootCountAfter = $bootAfter
    $summary.bootRestoredAlarm = $bootAlarm
    $bootPhase = if ($RebootOnly) { "release-reboot-recovery-$bootIteration" } else { 'release-reboot-recovery' }
    Complete-ReleasePhase $bootPhase
    }

    if (-not $RebootOnly) {
    # Проверка реальной доставки: задаём appops только нашей новой установке,
    # меняем период задачи через UI на минуту, подтверждаем
    # её в деталях для нового отсчёта от текущего ACK и уходим Home.
    # Ждём наступления срока и появления её уникального описания в системной шторке.
    # Receiver не вызывается вручную, время ОС не меняется, приватная база не читается.
    # InexactOnly сохраняет запрет exact; остальные режимы проверяют разрешённый exact.
    $inexactSince = (Invoke-ReleaseAdb @('shell', "date '+%m-%d %H:%M:%S.000'")).Trim()
    Invoke-ReleaseAdb @('shell', 'appops', 'set', $package, 'SCHEDULE_EXACT_ALARM', $(if ($InexactOnly) { 'deny' } else { 'allow' })) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    Open-ReleaseDetails $edited
    Open-ReleaseEdit
    Click-ReleaseId 'spinner_interval'
    Click-ReleaseNode "//node[@text='Минута']"
    Click-ReleaseId 'btn_save_task'
    Click-ReleaseId 'btnAckTask'
    Assert-ReleaseText $edited
    $notificationDeadline = Wait-ReleaseAlarm
    $summary.notificationAlarm = $notificationDeadline
    Write-Output "Waiting for real AlarmManager notification (deadline $notificationDeadline)..."
    Invoke-ReleaseAdb @('shell', 'input', 'keyevent', '3') | Out-Null
    if ($InexactOnly -and -not $DozeOnly) { Assert-ReleaseInexactDelivery $edited $notificationDeadline $inexactSince }
    if ($DozeOnly) { Assert-ReleaseDozeDelivery $edited $notificationDeadline $inexactSince }
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'expand-notifications') | Out-Null
    $notificationLimit = (Get-Date).AddSeconds(150)
    $notificationVisible = $false
    do {
        $notificationUi = Read-ReleaseShade
        $notificationVisible = $null -ne $notificationUi.SelectSingleNode("//node[@text='$edited']")
        if (-not $notificationVisible) { Start-Sleep -Seconds 2 }
    } while (-not $notificationVisible -and (Get-Date) -lt $notificationLimit)
    if (-not $notificationVisible) { throw 'Release task notification not delivered to shade' }
    Set-Content (Join-Path $run 'notifications-delivered.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    Complete-ReleasePhase 'release-notification-delivery'

    if ($PeriodicOnly) {
        Assert-ReleasePeriodicReminder $edited
    } else {

    # Проверка ACK из настоящего уведомления: нажимаем системную action Acknowledge,
    # которая отправляет PendingIntent release AckReceiver. Новый alarm должен стать
    # позднее сработавшего, описание остаётся в списке после нового запуска процесса.
    # Перед reboot appops не изменяли; allow/deny exact относится лишь к этой фазе
    # и исчезает вместе с удалением установленного нами APK в finally.
    Click-ReleaseNode "//node[@text='Acknowledge' or @text='ACKNOWLEDGE']"
    $notificationAckAlarm = Wait-ReleaseAlarm $notificationDeadline
    $afterNotificationAckUi = Read-ReleaseShade
    if ($afterNotificationAckUi.SelectSingleNode("//node[@text='$edited']")) { throw 'Acknowledged notification still visible in shade' }
    Set-Content (Join-Path $run 'notifications-after-ack.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'notification', '--noredact')) -Encoding UTF8
    $summary.notificationAckAlarm = $notificationAckAlarm
    Invoke-ReleaseAdb @('shell', 'cmd', 'statusbar', 'collapse') | Out-Null
    Invoke-ReleaseAdb @('shell', 'am', 'force-stop', $package) | Out-Null
    Launch-Release
    Assert-ReleaseText $edited
    if ((Wait-ReleaseAlarm) -ne $notificationAckAlarm) { throw 'Notification ACK alarm lost after restart' }
    Complete-ReleasePhase 'release-notification-ack'
    # После ACK/restart запрет должен сохраниться, новый alarm — иметь положительное
    # окно и тот же срок, который прочитан после ACK. Повторную доставку не ждём.
    if ($InexactOnly) {
        $opsAfterAck = Invoke-ReleaseAdb @('shell', 'appops', 'get', $package, 'SCHEDULE_EXACT_ALARM')
        Set-Content (Join-Path $run 'inexact-appops-after-ack.log') $opsAfterAck -Encoding UTF8
        if ($opsAfterAck -notmatch 'SCHEDULE_EXACT_ALARM: deny') { throw 'Exact denial lost after notification ACK' }
        $ackAlarms = Invoke-ReleaseAdb @('shell', 'dumpsys', 'alarm')
        Set-Content (Join-Path $run 'inexact-alarm-after-ack.log') $ackAlarms -Encoding UTF8
        $ackAlarm = [regex]::Match($ackAlarms, '(?m)^\s*RTC_WAKEUP #\d+: Alarm\{\S+ type 0 origWhen (?<deadline>\d+) [^\r\n]* com\.vpe_soft\.intime\.intime\}\r?\n\s+tag=\*walarm\*:com\.vpe_soft\.intime\.intime/(?:com\.vpe_soft\.intime\.intime)?\.receiver\.AlarmReceiver\r?\n\s+type=RTC_WAKEUP[^\r\n]*?window=(?<window>\S+)')
        if (-not $ackAlarm.Success -or [long]$ackAlarm.Groups['deadline'].Value -ne $notificationAckAlarm -or $ackAlarm.Groups['window'].Value -notmatch '[1-9]') { throw 'Notification ACK did not keep an inexact alarm' }
        $summary.inexactAckAlarmWindow = $ackAlarm.Groups['window'].Value
    }

    if (-not $DozeOnly -and -not $InexactOnly) {
    # Проверка отказа POST_NOTIFICATIONS: отзыв разрешения может убить процесс,
    # поэтому выполняем его до нового запуска. Флаги user-set/user-fixed исключают
    # всплывающий permission dialog. UI ACK ставит новый минутный alarm, Home;
    # receiver должен сработать, но описание не должно появиться в шторке даже
    # через пять секунд после срока. Задача остаётся в списке; базу не читаем.
    if ($TestApi -ge 33) {
        Invoke-ReleaseAdb @('shell', 'pm', 'revoke', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
        Invoke-ReleaseAdb @('shell', 'pm', 'set-permission-flags', $package, 'android.permission.POST_NOTIFICATIONS', 'user-set', 'user-fixed') | Out-Null
        $deniedPackage = Invoke-ReleaseAdb @('shell', 'dumpsys', 'package', $package)
        if ($deniedPackage -notmatch 'android.permission.POST_NOTIFICATIONS: granted=false') { throw 'Runtime permission denial not confirmed' }
        Set-Content (Join-Path $run 'permission-denied-package.log') $deniedPackage -Encoding UTF8
        $summary.notificationBlockMode = 'runtime-permission'
    } else {
        # API 31: runtime permission отсутствует. Выключаем общий доступ через
        # системный экран только собственной установки; проверяем переключатель
        # и importance=NONE, затем receiver и отсутствие уведомления.
        Set-ReleaseAppNotificationsEnabled $false
        $summary.notificationBlockMode = 'settings-switch'
    }
    $deniedAlarm = Prepare-ReleaseMinuteAlarm $edited
    Assert-ReleaseBlockedNotification $deniedAlarm $edited 'permission-denied'
    $summary.permissionDeniedAlarm = $deniedAlarm
    Complete-ReleasePhase $(if ($TestApi -ge 33) { 'release-permission-denied' } else { 'release-app-notifications-denied' })

    # Проверка снятия отказа: очищаем только выставленные нами флаги, возвращаем
    # POST_NOTIFICATIONS и через UI ACK создаём новый срок той же задачи. Настоящее
    # уведомление и его action ACK должны снова работать. Снятие отказа без нового
    # ACK у старой просрочки в этой фазе не проверяется.
    if ($TestApi -ge 33) {
        Invoke-ReleaseAdb @('shell', 'pm', 'clear-permission-flags', $package, 'android.permission.POST_NOTIFICATIONS', 'user-set', 'user-fixed') | Out-Null
        Invoke-ReleaseAdb @('shell', 'pm', 'grant', $package, 'android.permission.POST_NOTIFICATIONS') | Out-Null
    } else {
        # Снятие общего запрета API 31: включаем уведомления в Settings;
        # новый UI ACK существующей задачи должен восстановить доставку/action ACK.
        # Автоматическая доставка старой просрочки без ACK этим не проверяется.
        Set-ReleaseAppNotificationsEnabled $true
    }
    Assert-ReleaseRecoveredNotification $edited 'permission-restored'
    Complete-ReleasePhase $(if ($TestApi -ge 33) { 'release-permission-restored' } else { 'release-app-notifications-restored' })

    # Проверка отключённого канала: общий доступ к уведомлениям разрешён, канал
    # DefaultChannelId выключается через экран Android. Новый минутный alarm
    # должен дойти до receiver, но не создать видимое уведомление. Это отдельная
    # проверка канала; общий системный запрет всех уведомлений здесь не включается.
    Set-ReleaseChannelEnabled $false
    $channelAlarm = Prepare-ReleaseMinuteAlarm $edited
    Assert-ReleaseBlockedNotification $channelAlarm $edited 'channel-disabled'
    $summary.channelDisabledAlarm = $channelAlarm
    Complete-ReleasePhase 'release-channel-disabled'

    # Проверка включения канала: через тот же системный экран возвращаем разрешение
    # канала и начинаем новый срок UI ACK. Ожидаем реальное уведомление и успешный
    # ACK из шторки. Тест не читает wasNotified и не моделирует восстановление
    # старой просрочки автоматически, без пользовательского действия.
    Set-ReleaseChannelEnabled $true
    Assert-ReleaseRecoveredNotification $edited 'channel-restored'
    Complete-ReleasePhase 'release-channel-restored'
    }
    }
    }
    $summary.status = 'PASSED'
} catch {
    $summary.status = if ($installedHere) { 'FAILED' } else { 'INFRASTRUCTURE_ERROR' }
    $summary.message = $_.Exception.Message
    # Отсутствие естественного запуска к лимиту — неопределённый результат
    # планирования, а не доказанное падение приложения/R8. Остальные assertions
    # и ошибки диагностики/очистки по-прежнему считаются FAILED.
    if ($summary.message -like 'Natural release worker notification timed out*') { $summary.status = 'INCONCLUSIVE' }
    if ($summary.message -like 'Periodic observation interrupted*') { $summary.status = 'INFRASTRUCTURE_INTERRUPTED' }
    if ($summary.message -like 'Inexact observation interrupted*') { $summary.status = 'INFRASTRUCTURE_INTERRUPTED' }
    if ($summary.message -like 'Inexact alarm notification timed out*') { $summary.status = 'INCONCLUSIVE' }
    Write-Output $summary.message
} finally {
    if ($dozeChanged) {
        try {
            Set-Content (Join-Path $run 'doze-failure-state.log') (Invoke-ReleaseAdb @('shell', 'dumpsys', 'deviceidle')) -Encoding UTF8
        } catch { $summary.dozeDiagnosticError = $_.Exception.Message }
        try { Restore-ReleaseDoze } catch { $summary.dozeCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($installedHere) {
        try {
            $window = Invoke-ReleaseAdb @('shell', 'dumpsys', 'window')
            $logcat = Invoke-ReleaseAdb @('logcat', '-d')
            Set-Content (Join-Path $run 'window.log') $window -Encoding UTF8
            Set-Content (Join-Path $run 'logcat.log') $logcat -Encoding UTF8
            $beforeRebootLog = Join-Path $run 'logcat-before-reboot.log'
            $priorHealth = if (Test-Path $beforeRebootLog) { Get-Content $beforeRebootLog -Raw -Encoding UTF8 } else { '' }
            if ($window -match 'Application Not Responding:' -or $logcat -match 'ANR in|FATAL EXCEPTION' -or $priorHealth -match 'ANR in|FATAL EXCEPTION') {
                $summary.status = 'FAILED'; $summary.healthError = 'ANR or fatal exception in smoke logs'
            }
        } catch { $summary.healthError = $_.Exception.Message; $summary.status = 'FAILED' }
        # Ошибка сбора диагностики не должна мешать удалению нашей установки.
        try { Invoke-ReleaseAdb @('uninstall', $package) | Out-Null }
        catch { $summary.cleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($probePushed) {
        try { Invoke-ReleaseAdb @('shell', 'rm', '-f', '/data/local/tmp/intime-release-probe.jar', '/data/local/tmp/intime-release-ui.xml', '/data/local/tmp/local/tmp/intime-release-probe-output.xml') | Out-Null }
        catch { $summary.probeCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    if ($installedHere -and $backupDevicePath) {
        # Только уникальный файл этого запуска; сохранённая локальная копия остаётся в отчёте.
        try { Invoke-ReleaseAdb @('shell', 'rm', '-f', $backupDevicePath) | Out-Null }
        catch { $summary.backupCleanupError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    $env:JAVA_HOME = $oldJavaHome
    if ($lock) { $lock.Dispose() }
    if ($PeriodicOnly) {
        try { Set-ReleaseDispatchAssessment $summary $run }
        catch { $summary.dispatchAssessmentError = $_.Exception.Message; $summary.status = 'FAILED' }
    }
    $summary | ConvertTo-Json -Depth 6 | Set-Content (Join-Path $run 'summary.json') -Encoding UTF8
    Write-Output "$($summary.status). Reports: $run"
}
if ($summary.status -notin @('PASSED', 'PASSED_CONTROLLED_PLATFORM_DISPATCH')) { exit 1 }
